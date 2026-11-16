package com.aies.common;

/**
 * 报表缓存失效事件（D-36 细则：evaluation 域落库后发布，报表模块监听器删 key——
 * evaluation 域零 import 报表域，经 common 事件解耦；监听侧
 * @TransactionalEventListener(AFTER_COMMIT) 保证事务提交成功才失效）。
 *
 * @param taskId    复核/重评涉及的 taskId（教师 scope 缓存失效依据）
 * @param studentId 涉及的学生 id（学生 /me scope 缓存失效依据）
 */
public record ReportCacheEvictEvent(long taskId, long studentId) {
}
