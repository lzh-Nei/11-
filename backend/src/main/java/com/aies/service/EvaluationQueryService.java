package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.EvaluationHistoryPageVO;
import com.aies.dto.EvaluationHistoryVO;
import com.aies.dto.EvaluationVO;
import com.aies.entity.EvaluationHistory;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.entity.User;
import com.aies.entity.EvaluationAppeal;
import com.aies.mapper.EvaluationAppealMapper;
import com.aies.mapper.EvaluationHistoryMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 评价查询域（03-03 §七 E-01/E-04/E-05；UC-21报告页 + UC-25历史版本，W8-M1扩展）。
 *
 * <p>IDOR防线（S-05/04-04 §三"学生查询evaluation校验submission.student_id"）：
 * STUDENT 仅本人提交的评价可见，非本人/不存在一律40400同形防探测；
 * TEACHER/ADMIN（evaluation:view权限）放行全量并携带trace_id。
 * E-04/E-05同口径：STUDENT限本人；TEACHER须为task归属教师（spec决策2）；ADMIN放行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationQueryService {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_TEACHER = "TEACHER";

    /** E-04 limit 默认值与边界（UC-25：默认20，1~50越界→30000） */
    static final int DEFAULT_LIMIT = 20;
    static final int LIMIT_MIN = 1;
    static final int LIMIT_MAX = 50;

    private final EvaluationResultMapper evaluationResultMapper;
    private final EvaluationHistoryMapper evaluationHistoryMapper;
    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final EvaluationAppealMapper appealMapper;
    private final ObjectMapper objectMapper;

    /** E-01 评价当前值查询 */
    public EvaluationVO getById(long evaluationId, LoginPrincipal principal) {
        EvaluationResult result = evaluationResultMapper.selectById(evaluationId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Submission submission = submissionMapper.selectById(result.getSubmissionId());
        if (ROLE_STUDENT.equals(principal.role())
                && (submission == null || submission.getStudentId() == null
                || principal.userId() != submission.getStudentId())) {
            // IDOR：非本人提交的评价一律40400同形（不区分"不存在/无权"防探测）
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        EvaluationVO vo = new EvaluationVO();
        vo.setId(result.getId());
        vo.setSubmissionId(result.getSubmissionId());
        vo.setTaskId(submission == null ? null : submission.getTaskId());
        vo.setStatus(result.getStatus());
        vo.setCurrentVersionNo(result.getCurrentVersionNo());
        vo.setTotalScore(result.getTotalScore());
        vo.setMetricScores(parseMetrics(result.getMetricScores()));
        vo.setAiComment(result.getAiComment());
        vo.setAiModel(result.getAiModel());
        if (result.getReviewerId() != null) {
            User reviewer = userMapper.selectById(result.getReviewerId());
            vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
        }
        vo.setErrorCode(result.getErrorCode());
        vo.setUpdateTime(result.getUpdateTime());
        // 教师视角另含trace_id（E-01响应要点）
        if (!ROLE_STUDENT.equals(principal.role())) {
            vo.setTraceId(result.getTraceId());
        }
        return vo;
    }

    /** E-04 历史版本分页（UC-25：version_no倒序 + 不透明Base64游标 + limit钳制1~50） */
    public EvaluationHistoryPageVO getHistoryPage(long evaluationId, String cursor,
                                                  Integer limit, LoginPrincipal principal) {
        requireReadable(evaluationId, principal);
        int pageLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (pageLimit < LIMIT_MIN || pageLimit > LIMIT_MAX) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "limit越界（允许1~50）");
        }
        Long startExclusive = decodeCursor(cursor);

        LambdaQueryWrapper<EvaluationHistory> wrapper = new LambdaQueryWrapper<EvaluationHistory>()
                .eq(EvaluationHistory::getEvaluationId, evaluationId)
                .orderByDesc(EvaluationHistory::getVersionNo)
                .last("LIMIT " + (pageLimit + 1));
        if (startExclusive != null) {
            wrapper.lt(EvaluationHistory::getVersionNo, startExclusive);
        }
        List<EvaluationHistory> rows = evaluationHistoryMapper.selectList(wrapper);
        boolean hasMore = rows.size() > pageLimit;
        if (hasMore) {
            rows = rows.subList(0, pageLimit);
        }

        EvaluationHistoryPageVO page = new EvaluationHistoryPageVO();
        List<EvaluationHistoryVO> list = new ArrayList<>();
        for (EvaluationHistory row : rows) {
            list.add(toListItem(row));
        }
        page.setList(list);
        page.setHasMore(hasMore);
        page.setNextCursor(hasMore && !rows.isEmpty()
                ? encodeCursor(rows.get(rows.size() - 1).getVersionNo()) : null);
        return page;
    }

    /** E-05 单版本完整快照（含metric_scores/评语/理由；学生裁剪trace_id，UC-25备选流A2） */
    public EvaluationHistoryVO getVersion(long evaluationId, int versionNo, LoginPrincipal principal) {
        requireReadable(evaluationId, principal);
        EvaluationHistory row = evaluationHistoryMapper.selectOne(
                new LambdaQueryWrapper<EvaluationHistory>()
                        .eq(EvaluationHistory::getEvaluationId, evaluationId)
                        .eq(EvaluationHistory::getVersionNo, versionNo));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        EvaluationHistoryVO vo = toListItem(row);
        vo.setMetricScores(parseMetrics(row.getMetricScores()));
        vo.setAiComment(row.getAiComment());
        vo.setTeacherComment(row.getTeacherComment());
        // traceId仅教师/管理员视角（学生裁剪，UC-25 A2；教师可跳留痕回放抽屉）
        if (!ROLE_STUDENT.equals(principal.role())) {
            vo.setTraceId(row.getTraceId());
        }
        return vo;
    }

    /* ==================== 私有：归属与游标 ==================== */

    /** E-04/E-05归属校验：result不存在→40400；STUDENT非本人→40400同形；
     *  TEACHER须为task归属教师（spec决策2）否则40400；ADMIN放行 */
    private void requireReadable(long evaluationId, LoginPrincipal principal) {
        EvaluationResult result = evaluationResultMapper.selectById(evaluationId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Submission submission = submissionMapper.selectById(result.getSubmissionId());
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (ROLE_STUDENT.equals(principal.role())
                && (submission.getStudentId() == null
                || principal.userId() != submission.getStudentId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (ROLE_TEACHER.equals(principal.role())) {
            Task task = taskMapper.selectById(submission.getTaskId());
            boolean owner = task != null && task.getTeacherId() != null
                    && task.getTeacherId() == principal.userId();
            if (!owner) {
                EvaluationAppeal appeal = appealMapper.selectOne(
                        new LambdaQueryWrapper<EvaluationAppeal>()
                                .eq(EvaluationAppeal::getEvaluationId, evaluationId)
                                .eq(EvaluationAppeal::getAssignedTeacherId, principal.userId())
                                .eq(EvaluationAppeal::getStatus, "PENDING")
                                .last("LIMIT 1"));
                if (appeal == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
            }
        }
    }

    /** 游标解码：Base64(URL-safe)→起始version_no；解码失败→30000（UC-25 E2） */
    private Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(new String(Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "cursor格式非法");
        }
    }

    private String encodeCursor(long versionNo) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(versionNo).getBytes(StandardCharsets.UTF_8));
    }

    /** 列表项装配（E-04列表场景字段；reviewer_name查sys_user.real_name，AI版本null） */
    private EvaluationHistoryVO toListItem(EvaluationHistory row) {
        EvaluationHistoryVO vo = new EvaluationHistoryVO();
        vo.setVersionNo(row.getVersionNo());
        vo.setSource(row.getSource());
        vo.setTotalScore(row.getTotalScore());
        vo.setAiModel(row.getAiModel());
        vo.setCreateTime(row.getCreateTime());
        if (row.getReviewerId() != null) {
            User reviewer = userMapper.selectById(row.getReviewerId());
            vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
        }
        return vo;
    }

    /** metric_scores JSON→Map（脏数据兜底：解析失败返回空Map不阻断报告页） */
    private Map<String, BigDecimal> parseMetrics(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("metric_scores解析失败(返回空): {}", e.getMessage());
            return Map.of();
        }
    }
}
