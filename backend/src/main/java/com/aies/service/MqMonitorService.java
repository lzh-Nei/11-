package com.aies.service;

import com.aies.config.RabbitConfig;
import com.aies.dto.admin.AdminDtos.MqAlertVO;
import com.aies.dto.admin.AdminDtos.MqEventVO;
import com.aies.dto.admin.AdminDtos.MqMonitorVO;
import com.aies.dto.admin.AdminDtos.MqQueueVO;
import com.aies.dto.admin.AdminDtos.MqStatsVO;
import com.aies.entity.EvaluationResult;
import com.aies.entity.Submission;
import com.aies.entity.Task;
import com.aies.entity.User;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端消息队列监控（system:config）：只读观察，不消费、不声明、不改队列。
 *
 * <p>队列水位经 RabbitAdmin#getQueueInfo 读取 broker（ready/consumers）；
 * 数据库侧按 submission.parse_status 与 evaluation_result.status 汇总流水，
 * 最近事件取最新提交并合并关联任务/学生/评价。RabbitMQ 不可用时接口仍返回
 * 数据库部分，队列侧以 available=false + error 呈现，避免监控页拖垮后台接口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqMonitorService {

    private static final int RECENT_LIMIT = 12;

    private final RabbitAdmin rabbitAdmin;
    private final SubmissionMapper submissionMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final MqAlertMonitor alertMonitor;

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitPort;

    public MqMonitorVO status() {
        List<MqQueueVO> queues = new ArrayList<>(4);
        queues.add(queueInfo("parse", "文件解析队列", RabbitConfig.PARSE_QUEUE,
                RabbitConfig.PARSE_EXCHANGE, RabbitConfig.PARSE_ROUTING_KEY));
        queues.add(queueInfo("eval", "AI 评价队列", RabbitConfig.EVAL_QUEUE,
                RabbitConfig.EVAL_EXCHANGE, RabbitConfig.EVAL_ROUTING_KEY));
        // 死信队列同样纳入监控：积压意味着有消息重试耗尽或不可路由，需要人工处置
        queues.add(queueInfo("parseDlq", "解析死信队列", RabbitConfig.PARSE_DLQ,
                RabbitConfig.DLX_EXCHANGE, RabbitConfig.PARSE_DEAD_ROUTING_KEY));
        queues.add(queueInfo("evalDlq", "评价死信队列", RabbitConfig.EVAL_DLQ,
                RabbitConfig.DLX_EXCHANGE, RabbitConfig.EVAL_DEAD_ROUTING_KEY));
        boolean connected = queues.stream().anyMatch(MqQueueVO::available);
        MqAlertMonitor.AlertState alert = alertMonitor.evaluate(
                readyCount(queues, "parse"), readyCount(queues, "eval"),
                readyCount(queues, "parseDlq"), readyCount(queues, "evalDlq"));
        return new MqMonitorVO(connected, rabbitHost + ":" + rabbitPort, LocalDateTime.now(),
                queues, new MqAlertVO(alert.alert(), alert.level(), alert.messages()),
                stats(), recentEvents());
    }

    /** 从已抓取的队列快照取 ready 数，避免为告警重复查询 broker */
    private long readyCount(List<MqQueueVO> queues, String key) {
        return queues.stream().filter(q -> q.key().equals(key))
                .findFirst()
                .map(q -> q.readyMessages() == null ? 0L : q.readyMessages().longValue())
                .orElse(0L);
    }

    private MqQueueVO queueInfo(String key, String label, String queueName,
                                String exchange, String routingKey) {
        try {
            QueueInformation info = rabbitAdmin.getQueueInfo(queueName);
            if (info == null) {
                return new MqQueueVO(key, label, queueName, exchange, routingKey,
                        false, null, null, "队列尚未在 broker 中创建");
            }
            return new MqQueueVO(key, label, queueName, exchange, routingKey,
                    true, info.getMessageCount(), info.getConsumerCount(), null);
        } catch (Exception e) {
            log.warn("MQ监控读取队列失败: queue={} err={}", queueName, e.getMessage());
            return new MqQueueVO(key, label, queueName, exchange, routingKey,
                    false, null, null, "无法连接消息队列服务");
        }
    }

    private MqStatsVO stats() {
        return new MqStatsVO(
                countSubmission("QUEUED"),
                countSubmission("SUCCESS"),
                countSubmission("FAILED"),
                countEvaluation("PENDING"),
                countEvaluation("AI_DONE"),
                countEvaluation("REVIEWED"),
                countEvaluation("FAILED"));
    }

    private long countSubmission(String status) {
        return submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getParseStatus, status));
    }

    private long countEvaluation(String status) {
        return evaluationResultMapper.selectCount(new LambdaQueryWrapper<EvaluationResult>()
                .eq(EvaluationResult::getStatus, status));
    }

    private List<MqEventVO> recentEvents() {
        List<Submission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .and(w -> w.eq(Submission::getCanceled, 0)
                                .or().isNull(Submission::getCanceled))
                        .orderByDesc(Submission::getCreateTime)
                        .last("LIMIT " + RECENT_LIMIT));
        if (submissions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Task> tasks = byId(taskMapper.selectBatchIds(
                submissions.stream().map(Submission::getTaskId).filter(Objects::nonNull)
                        .collect(Collectors.toSet())), Task::getId);
        Map<Long, User> students = byId(userMapper.selectBatchIds(
                submissions.stream().map(Submission::getStudentId).filter(Objects::nonNull)
                        .collect(Collectors.toSet())), User::getId);
        Map<Long, EvaluationResult> results = evaluationResultMapper.selectList(
                        new LambdaQueryWrapper<EvaluationResult>()
                                .in(EvaluationResult::getSubmissionId,
                                        submissions.stream().map(Submission::getId)
                                                .collect(Collectors.toSet())))
                .stream().collect(Collectors.toMap(EvaluationResult::getSubmissionId,
                        Function.identity(), (a, b) -> a));
        return submissions.stream().map(s -> {
            Task task = tasks.get(s.getTaskId());
            User student = students.get(s.getStudentId());
            EvaluationResult result = results.get(s.getId());
            LocalDateTime updateTime = result != null && result.getUpdateTime() != null
                    ? result.getUpdateTime() : s.getCreateTime();
            return new MqEventVO(s.getId(), s.getFileName(),
                    student == null ? null : student.getRealName(),
                    task == null ? null : task.getTitle(),
                    s.getParseStatus(),
                    result == null ? null : result.getStatus(),
                    result == null ? null : result.getTotalScore(),
                    result == null ? null : result.getAiModel(),
                    updateTime);
        }).toList();
    }

    private <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idGetter) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.stream().collect(Collectors.toMap(idGetter, Function.identity(), (a, b) -> a));
    }
}
