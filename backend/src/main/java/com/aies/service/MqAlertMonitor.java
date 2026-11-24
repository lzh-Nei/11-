package com.aies.service;

import com.aies.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MQ 积压告警巡检：定时读取主队列与死信队列水位。
 *
 * <p>死信达到阈值或主队列积压超过阈值时输出 WARN；告警内容变化才重复输出，
 * 恢复后输出一次 INFO，避免每轮巡检刷屏。RabbitMQ 不可用时降级为一条读取失败告警，
 * 不影响业务线程。管理端监控页也会基于同一批队列水位展示页面告警。</p>
 */
@Slf4j
@Service
public class MqAlertMonitor {

    private final RabbitAdmin rabbitAdmin;
    private final long dlqWarnThreshold;
    private final long backlogWarnThreshold;
    private final AtomicReference<String> lastAlert = new AtomicReference<>("");

    public MqAlertMonitor(RabbitAdmin rabbitAdmin,
                          @Value("${mq.alert.dlq-warn-threshold:1}") long dlqWarnThreshold,
                          @Value("${mq.alert.backlog-warn-threshold:100}") long backlogWarnThreshold) {
        this.rabbitAdmin = rabbitAdmin;
        this.dlqWarnThreshold = dlqWarnThreshold;
        this.backlogWarnThreshold = backlogWarnThreshold;
    }

    @Scheduled(fixedDelayString = "${mq.alert.check-ms:300000}",
            initialDelayString = "${mq.alert.initial-ms:60000}")
    public void check() {
        AlertState state = evaluate();
        String previous = lastAlert.getAndSet(state.signature());
        if (state.signature().isEmpty()) {
            if (!previous.isEmpty()) {
                log.info("MQ队列告警已恢复: {}", previous);
            }
            return;
        }
        if (!state.signature().equals(previous)) {
            log.warn("MQ队列告警: {}", String.join("；", state.messages()));
        }
    }

    /** 读取 broker 水位并计算告警；broker 不可用时降级为一条 ERROR 告警 */
    public AlertState evaluate() {
        try {
            return evaluate(messageCount(RabbitConfig.PARSE_QUEUE), messageCount(RabbitConfig.EVAL_QUEUE),
                    messageCount(RabbitConfig.PARSE_DLQ), messageCount(RabbitConfig.EVAL_DLQ));
        } catch (RuntimeException e) {
            List<String> messages = List.of("队列水位读取失败: " + e.getMessage());
            return new AlertState(true, "ERROR", String.join("|", messages), messages);
        }
    }

    /** 基于已获取的队列水位计算告警（管理端快照复用，避免重复查询 broker） */
    public AlertState evaluate(long parseReady, long evalReady, long parseDlq, long evalDlq) {
        List<String> messages = new ArrayList<>();
        boolean deadLetter = false;
        if (parseReady > backlogWarnThreshold) {
            messages.add("解析队列积压 " + parseReady + " 条");
        }
        if (evalReady > backlogWarnThreshold) {
            messages.add("评价队列积压 " + evalReady + " 条");
        }
        if (parseDlq >= dlqWarnThreshold) {
            messages.add("解析死信 " + parseDlq + " 条");
            deadLetter = true;
        }
        if (evalDlq >= dlqWarnThreshold) {
            messages.add("评价死信 " + evalDlq + " 条");
            deadLetter = true;
        }
        String level = messages.isEmpty() ? "OK" : (deadLetter ? "ERROR" : "WARN");
        return new AlertState(!messages.isEmpty(), level, String.join("|", messages),
                List.copyOf(messages));
    }

    private long messageCount(String queueName) {
        QueueInformation info = rabbitAdmin.getQueueInfo(queueName);
        return info == null ? 0L : info.getMessageCount();
    }

    /** 告警快照：signature 用于状态变化去重 */
    public record AlertState(boolean alert, String level, String signature, List<String> messages) {
    }
}
