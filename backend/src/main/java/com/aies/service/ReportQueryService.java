package com.aies.service;

import com.aies.common.ReportCacheEvictEvent;
import com.aies.entity.EvaluationHistory;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.mapper.EvaluationHistoryMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.model.ReportOverviewDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表查询服务（UC-25，C1 v1.0 D-36 裁决版）。
 *
 * <p>数据口径（LLD §五）：仅取 evaluation_result 当前值；FAILED 计入提交率分母、
 * 不计入均分；分差分布=history 最新教师版本（TEACHER_REVIEW/RE_EVALUATE）−
 * 最新 AI_INITIAL 版本。聚合在内存完成（班级级数据量级，等价 LLD SQL 口径）。</p>
 *
 * <p>缓存（D-36 双保险）：teacher/me 两 scope，key=report:overview:{scope}:{userId}，
 * TTL 10min 兜底 + 落库侧 ReportCacheEvictEvent 主动失效（AFTER_COMMIT 监听，
 * 事务提交成功才删——杜绝"删缓存但落库回滚→旧值回填"错序）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportQueryService {

    static final String CACHE_PREFIX = "report:overview:";
    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    /** result 计入均分/分布的状态集（FAILED/PENDING 排除） */
    static final List<String> SCORED_STATUS = List.of("AI_DONE", "REVIEWED");

    private final TaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final EvaluationHistoryMapper evaluationHistoryMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 教师四图聚合（任教任务范围）。 */
    public ReportOverviewDTO overviewForTeacher(long teacherId) {
        return cached("teacher:" + teacherId, () -> {
            List<Task> tasks = taskMapper.selectList(
                    new LambdaQueryWrapper<Task>().eq(Task::getTeacherId, teacherId));
            if (tasks.isEmpty()) {
                return ReportOverviewDTO.empty();
            }
            List<Submission> subs = submissionMapper.selectList(
                    new LambdaQueryWrapper<Submission>().in(Submission::getTaskId, taskIds(tasks)));
            return aggregate(tasks, subs);
        });
    }

    /** 学生个人四图聚合（仅本人提交，/me 端点设计层消除 studentId 暴露——D-36 双重点赞口径）。 */
    public ReportOverviewDTO overviewForMe(long studentId) {
        return cached("me:" + studentId, () -> {
            List<Submission> subs = submissionMapper.selectList(
                    new LambdaQueryWrapper<Submission>().eq(Submission::getStudentId, studentId));
            if (subs.isEmpty()) {
                return ReportOverviewDTO.empty();
            }
            List<Long> taskIds = subs.stream().map(Submission::getTaskId).distinct().toList();
            List<Task> tasks = taskMapper.selectBatchIds(taskIds);
            return aggregate(tasks, subs);
        });
    }

    /** 强制刷新（UC-30 对标）：删除 scope 缓存，下次查询重建。 */
    public void refresh(String scope, long userId) {
        redis.delete(CACHE_PREFIX + scope + ":" + userId);
        log.info("报表缓存已强制失效: scope={} userId={}", scope, userId);
    }

    /**
     * 失效事件监听（AFTER_COMMIT：事务提交成功才失效，防删了缓存落库回滚→旧值回填）。
     * 盲点1防御（PM预警三坑之一）：afterCommit 阶段删 Redis 失败时 DB 已提交，
     * 异常必须吞噬（try-catch+日志），靠 TTL 10min 兜底——不允许传播回主流程
     * （否则调用方收到异常但数据实际已变更）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCacheEvict(ReportCacheEvictEvent event) {
        try {
            redis.delete(CACHE_PREFIX + "me:" + event.studentId());
            Task task = taskMapper.selectById(event.taskId());
            if (task != null && task.getTeacherId() != null) {
                redis.delete(CACHE_PREFIX + "teacher:" + task.getTeacherId());
            }
            log.info("报表缓存事件失效完成: taskId={} studentId={}", event.taskId(), event.studentId());
        } catch (Exception e) {
            log.warn("报表缓存事件失效失败（TTL 10min 兜底收敛）: taskId={} studentId={} err={}",
                    event.taskId(), event.studentId(), e.getMessage());
        }
    }

    private interface DtoBuilder {
        ReportOverviewDTO build();
    }

    private ReportOverviewDTO cached(String keySuffix, DtoBuilder builder) {
        String key = CACHE_PREFIX + keySuffix;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ReportOverviewDTO.class);
            } catch (Exception e) {
                log.warn("报表缓存反序列化失败，重建: key={} err={}", key, e.getMessage());
            }
        }
        ReportOverviewDTO dto = builder.build();
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(dto), CACHE_TTL);
        } catch (Exception e) {
            log.warn("报表缓存写入失败（TTL 兜底不受影响）: key={} err={}", key, e.getMessage());
        }
        return dto;
    }

    /** 四图聚合核心（内存等价 LLD SQL 口径）。 */
    private ReportOverviewDTO aggregate(List<Task> tasks, List<Submission> subs) {
        List<Long> subIds = subs.stream().map(Submission::getId).toList();
        Map<Long, EvaluationResult> resultMap = subIds.isEmpty() ? Map.of()
                : evaluationResultMapper.selectList(
                        new LambdaQueryWrapper<EvaluationResult>()
                                .in(EvaluationResult::getSubmissionId, subIds))
                        .stream()
                        .collect(Collectors.toMap(EvaluationResult::getSubmissionId, r -> r, (a, b) -> a));
        Map<Long, Task> taskMap = tasks.stream()
                .collect(Collectors.toMap(Task::getId, t -> t));

        // 图1 任务级统计（submitted=全部解析链状态提交；rate=submitted/total*100，total暂以提交数为分母占位）
        List<ReportOverviewDTO.TaskStat> taskStats = tasks.stream().map(t -> {
            List<Submission> taskSubs = subs.stream()
                    .filter(s -> t.getId().equals(s.getTaskId())).toList();
            long scored = taskSubs.stream()
                    .filter(s -> resultMap.containsKey(s.getId())
                            && SCORED_STATUS.contains(resultMap.get(s.getId()).getStatus()))
                    .count();
            BigDecimal avg = scored == 0 ? BigDecimal.ZERO
                    : taskSubs.stream()
                            .filter(s -> resultMap.containsKey(s.getId())
                                    && SCORED_STATUS.contains(resultMap.get(s.getId()).getStatus()))
                            .map(s -> resultMap.get(s.getId()).getTotalScore())
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(scored), 1, RoundingMode.HALF_UP);
            return new ReportOverviewDTO.TaskStat(t.getId(), t.getTitle(),
                    taskSubs.size(), (int) scored, avg,
                    BigDecimal.valueOf(taskSubs.size())
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(Math.max(taskSubs.size(), 1)), 1, RoundingMode.HALF_UP));
        }).toList();

        // 图2 均分分布（0-59/60-69/70-79/80-89/90-100）
        List<BigDecimal> scores = resultMap.values().stream()
                .filter(r -> SCORED_STATUS.contains(r.getStatus()) && r.getTotalScore() != null)
                .map(EvaluationResult::getTotalScore).toList();
        List<ReportOverviewDTO.RangeCount> scoreDist = List.of(
                new ReportOverviewDTO.RangeCount("0-59", scores.stream().filter(s -> s.compareTo(bd(60)) < 0).count()),
                new ReportOverviewDTO.RangeCount("60-69", scores.stream().filter(s -> s.compareTo(bd(60)) >= 0 && s.compareTo(bd(70)) < 0).count()),
                new ReportOverviewDTO.RangeCount("70-79", scores.stream().filter(s -> s.compareTo(bd(70)) >= 0 && s.compareTo(bd(80)) < 0).count()),
                new ReportOverviewDTO.RangeCount("80-89", scores.stream().filter(s -> s.compareTo(bd(80)) >= 0 && s.compareTo(bd(90)) < 0).count()),
                new ReportOverviewDTO.RangeCount("90-100", scores.stream().filter(s -> s.compareTo(bd(90)) >= 0).count()));

        // 图3 指标雷达（metric_scores JSON 逐键均分；解析失败行跳过并留痕——聚合容错不阻塞报表）
        Map<String, List<BigDecimal>> metricAgg = new LinkedHashMap<>();
        for (EvaluationResult r : resultMap.values()) {
            if (!SCORED_STATUS.contains(r.getStatus()) || r.getMetricScores() == null) {
                continue;
            }
            try {
                Map<String, BigDecimal> ms = objectMapper.readValue(r.getMetricScores(),
                        new TypeReference<Map<String, BigDecimal>>() {
                        });
                ms.forEach((k, v) -> metricAgg.computeIfAbsent(k, x -> new ArrayList<>()).add(v));
            } catch (Exception e) {
                log.warn("metric_scores解析失败跳过该行: submissionId={} err={}",
                        r.getSubmissionId(), e.getMessage());
            }
        }
        List<ReportOverviewDTO.RadarPoint> radar = metricAgg.entrySet().stream()
                .map(e -> new ReportOverviewDTO.RadarPoint(e.getKey(),
                        e.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(e.getValue().size()), 1, RoundingMode.HALF_UP)))
                .toList();

        // 图4 分差分布（教师最新版 − AI_INITIAL 最新版；仅双方都存在的提交计段）
        List<ReportOverviewDTO.RangeCount> gapDist = buildGapDist(subIds);

        return new ReportOverviewDTO(taskStats, scoreDist, radar, gapDist);
    }

    /** 分差段：≤5 / 6-10 / 11-20 / >20 / 无复核对（教师未复核不计段）。 */
    private List<ReportOverviewDTO.RangeCount> buildGapDist(List<Long> subIds) {
        List<EvaluationHistory> histories = subIds.isEmpty() ? List.of()
                : evaluationHistoryMapper.selectList(
                        new LambdaQueryWrapper<EvaluationHistory>()
                                .in(EvaluationHistory::getSubmissionId, subIds));
        Map<Long, List<EvaluationHistory>> bySub = histories.stream()
                .collect(Collectors.groupingBy(EvaluationHistory::getSubmissionId));
        List<BigDecimal> gaps = new ArrayList<>();
        for (List<EvaluationHistory> group : bySub.values()) {
            var ai = group.stream().filter(h -> "AI_INITIAL".equals(h.getSource()))
                    .max(Comparator.comparing(EvaluationHistory::getVersionNo));
            var teacher = group.stream().filter(h ->
                            "TEACHER_REVIEW".equals(h.getSource()) || "RE_EVALUATE".equals(h.getSource()))
                    .max(Comparator.comparing(EvaluationHistory::getVersionNo));
            if (ai.isPresent() && teacher.isPresent()
                    && ai.get().getTotalScore() != null && teacher.get().getTotalScore() != null) {
                gaps.add(teacher.get().getTotalScore().subtract(ai.get().getTotalScore()).abs());
            }
        }
        return List.of(
                new ReportOverviewDTO.RangeCount("≤5", gaps.stream().filter(g -> g.compareTo(bd(5)) <= 0).count()),
                new ReportOverviewDTO.RangeCount("6-10", gaps.stream().filter(g -> g.compareTo(bd(5)) > 0 && g.compareTo(bd(10)) <= 0).count()),
                new ReportOverviewDTO.RangeCount("11-20", gaps.stream().filter(g -> g.compareTo(bd(10)) > 0 && g.compareTo(bd(20)) <= 0).count()),
                new ReportOverviewDTO.RangeCount(">20", gaps.stream().filter(g -> g.compareTo(bd(20)) > 0).count()),
                new ReportOverviewDTO.RangeCount("无复核", Math.max(subIds.size() - gaps.size(), 0)));
    }

    private static BigDecimal bd(int v) {
        return BigDecimal.valueOf(v);
    }

    private static List<Long> taskIds(List<Task> tasks) {
        return tasks.stream().map(Task::getId).toList();
    }
}
