package com.aies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 维护类定时任务（唯一 @EnableScheduling 入口见 SchedulingConfig）。
 *
 * <p>职责：
 * ① 任务截止自动关闭：PUBLISHED 且 deadline 已过 → CLOSED，默认每分钟；
 * ② 异步链超时补偿：解析 PENDING/QUEUED、评价 PENDING 超时重投，默认每 5 分钟。
 * 参数全部可在 application.yml 的 schedule.* 覆盖。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMaintenanceService {

    private final TaskService taskService;
    private final AsyncRecoveryService recoveryService;

    @Value("${schedule.parse-pending-minutes:10}")
    private int parsePendingMinutes;
    @Value("${schedule.parse-queued-minutes:30}")
    private int parseQueuedMinutes;
    @Value("${schedule.eval-pending-minutes:15}")
    private int evalPendingMinutes;
    @Value("${schedule.recovery-batch:50}")
    private int recoveryBatch;

    /** 任务截止自动关闭（UC-10 deadline 语义闭环） */
    @Scheduled(fixedDelayString = "${schedule.task-close-ms:60000}",
            initialDelayString = "${schedule.task-close-initial-ms:30000}")
    public void closeExpiredTasks() {
        int closed = taskService.closeExpiredTasks(LocalDateTime.now());
        if (closed > 0) {
            log.info("定时任务：自动关闭过期任务 {} 个", closed);
        }
    }

    /** 异步链超时补偿：解析 PENDING/QUEUED + 评价 PENDING 重投 */
    @Scheduled(fixedDelayString = "${schedule.recovery-ms:300000}",
            initialDelayString = "${schedule.recovery-initial-ms:60000}")
    public void recoverStuckAsyncTasks() {
        LocalDateTime now = LocalDateTime.now();
        int parse = recoveryService.recoverStuckParses(
                now, parsePendingMinutes, parseQueuedMinutes, recoveryBatch);
        int eval = recoveryService.recoverStuckEvaluations(now, evalPendingMinutes, recoveryBatch);
        if (parse + eval > 0) {
            log.info("定时任务：异步链补偿 parse={} eval={}", parse, eval);
        }
    }
}
