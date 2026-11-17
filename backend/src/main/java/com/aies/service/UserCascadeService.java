package com.aies.service;

import com.aies.dto.admin.AdminDtos.UserDeletionImpactVO;
import com.aies.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户关联数据清理（管理员强制删除专用）。
 *
 * <p>普通删除仍由 AdminUserService.delete 保护：有业务数据就拒绝。强制删除必须在
 * 管理端二次确认后调用；本服务在外键依赖逆序中清理班级/任务/提交/评价/申诉/审计等
 * 记录，并把被删教师在他人评价中的复核痕迹匿名化（reviewer_id=NULL），避免误删其他
 * 学生的评价结果。整个过程与用户删除同一事务，失败整体回滚。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCascadeService {

    private final JdbcTemplate jdbc;

    /** 删除前快照：用于管理端告知影响范围 */
    public UserDeletionImpactVO impact(User user, List<String> roleCodes,
                                       boolean protectedAccount, String protectedReason) {
        Snapshot snapshot = snapshot(user.getId());
        long histories = countByEvaluationIds(snapshot.evaluationIds());
        long appeals = countAppeals(user.getId(), snapshot);
        long auditLogs = count("SELECT COUNT(*) FROM audit_log WHERE operator_id=?", user.getId());
        long reportExports = count("SELECT COUNT(*) FROM report_export_task WHERE requester_id=?",
                user.getId());
        long reviewReferences = count("SELECT COUNT(*) FROM evaluation_result WHERE reviewer_id=?",
                user.getId())
                + count("SELECT COUNT(*) FROM evaluation_history WHERE reviewer_id=?", user.getId());
        long unboundStudents = 0;
        if (!snapshot.classIds().isEmpty()) {
            List<Object> unboundArgs = new ArrayList<>();
            unboundArgs.add(user.getId());
            unboundArgs.addAll(snapshot.classIds());
            unboundStudents = count("SELECT COUNT(*) FROM sys_user WHERE id<>? AND class_id IN ("
                    + placeholders(snapshot.classIds()) + ")", unboundArgs.toArray());
        }
        long total = snapshot.classIds().size() + snapshot.taskIds().size()
                + snapshot.submissionIds().size() + snapshot.evaluationIds().size()
                + histories + appeals + auditLogs + reportExports + reviewReferences;
        return new UserDeletionImpactVO(user.getId(), user.getUsername(), user.getRealName(),
                List.copyOf(roleCodes), protectedAccount, protectedReason,
                snapshot.classIds().size(), snapshot.taskIds().size(),
                snapshot.submissionIds().size(), snapshot.evaluationIds().size(),
                histories, appeals, auditLogs, reportExports, reviewReferences,
                unboundStudents, total);
    }

    /** 强制清理并删除用户（AdminUserService 负责权限/自删校验与审计） */
    @Transactional
    public void forceDelete(long userId) {
        Snapshot snapshot = snapshot(userId);

        // 1. 保留其他学生评价，仅匿名化该教师在评价中的复核痕迹
        jdbc.update("UPDATE evaluation_history SET reviewer_id=NULL WHERE reviewer_id=?", userId);
        jdbc.update("UPDATE evaluation_result SET reviewer_id=NULL, review_time=NULL WHERE reviewer_id=?",
                userId);

        // 2. 申诉依赖评价/提交，必须先删
        List<Object> appealArgs = new ArrayList<>();
        StringBuilder appealSql = new StringBuilder(
                "DELETE FROM evaluation_appeal WHERE student_id=? OR assigned_teacher_id=?");
        appealArgs.add(userId);
        appealArgs.add(userId);
        appendIn(appealSql, appealArgs, "submission_id", snapshot.submissionIds());
        appendIn(appealSql, appealArgs, "evaluation_id", snapshot.evaluationIds());
        jdbc.update(appealSql.toString(), appealArgs.toArray());

        // 3. 评价历史 → 当前值 → 提交
        if (!snapshot.evaluationIds().isEmpty()) {
            jdbc.update("DELETE FROM evaluation_history WHERE evaluation_id IN ("
                    + placeholders(snapshot.evaluationIds()) + ")",
                    snapshot.evaluationIds().toArray());
            jdbc.update("DELETE FROM evaluation_result WHERE id IN ("
                    + placeholders(snapshot.evaluationIds()) + ")",
                    snapshot.evaluationIds().toArray());
        }
        if (!snapshot.submissionIds().isEmpty()) {
            jdbc.update("DELETE FROM evaluation_history WHERE submission_id IN ("
                    + placeholders(snapshot.submissionIds()) + ")",
                    snapshot.submissionIds().toArray());
            jdbc.update("DELETE FROM submission WHERE id IN ("
                    + placeholders(snapshot.submissionIds()) + ")",
                    snapshot.submissionIds().toArray());
        }

        // 4. 任务指标 → 任务 → 解除班级学生绑定 → 班级
        if (!snapshot.taskIds().isEmpty()) {
            jdbc.update("DELETE FROM task_metric WHERE task_id IN ("
                    + placeholders(snapshot.taskIds()) + ")",
                    snapshot.taskIds().toArray());
            jdbc.update("DELETE FROM task WHERE id IN ("
                    + placeholders(snapshot.taskIds()) + ")",
                    snapshot.taskIds().toArray());
        }
        if (!snapshot.classIds().isEmpty()) {
            jdbc.update("UPDATE sys_user SET class_id=NULL WHERE class_id IN ("
                    + placeholders(snapshot.classIds()) + ")",
                    snapshot.classIds().toArray());
            jdbc.update("DELETE FROM `class` WHERE id IN ("
                    + placeholders(snapshot.classIds()) + ")",
                    snapshot.classIds().toArray());
        }

        // 5. 其余直接外键记录 → 角色 → 用户
        jdbc.update("DELETE FROM report_export_task WHERE requester_id=?", userId);
        jdbc.update("DELETE FROM audit_log WHERE operator_id=?", userId);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
        log.info("用户强制删除完成: userId={} classes={} tasks={} submissions={} evaluations={}",
                userId, snapshot.classIds().size(), snapshot.taskIds().size(),
                snapshot.submissionIds().size(), snapshot.evaluationIds().size());
    }

    private Snapshot snapshot(long userId) {
        List<Long> classIds = ids("SELECT id FROM `class` WHERE teacher_id=?", userId);
        List<Long> taskIds;
        if (classIds.isEmpty()) {
            taskIds = ids("SELECT id FROM task WHERE teacher_id=?", userId);
        } else {
            taskIds = ids("SELECT id FROM task WHERE teacher_id=? OR class_id IN ("
                            + placeholders(classIds) + ")",
                    prepend(userId, classIds));
        }
        List<Long> submissionIds;
        if (taskIds.isEmpty()) {
            submissionIds = ids("SELECT id FROM submission WHERE student_id=?", userId);
        } else {
            submissionIds = ids("SELECT id FROM submission WHERE student_id=? OR task_id IN ("
                            + placeholders(taskIds) + ")",
                    prepend(userId, taskIds));
        }
        List<Long> evaluationIds = submissionIds.isEmpty() ? List.of()
                : ids("SELECT id FROM evaluation_result WHERE submission_id IN ("
                        + placeholders(submissionIds) + ")",
                        submissionIds.toArray());
        return new Snapshot(classIds, taskIds, submissionIds, evaluationIds);
    }

    private long countAppeals(long userId, Snapshot snapshot) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM evaluation_appeal WHERE student_id=? OR assigned_teacher_id=?");
        args.add(userId);
        args.add(userId);
        appendIn(sql, args, "submission_id", snapshot.submissionIds());
        appendIn(sql, args, "evaluation_id", snapshot.evaluationIds());
        return count(sql.toString(), args.toArray());
    }

    private long countByEvaluationIds(List<Long> evaluationIds) {
        if (evaluationIds.isEmpty()) {
            return 0;
        }
        return count("SELECT COUNT(*) FROM evaluation_history WHERE evaluation_id IN ("
                + placeholders(evaluationIds) + ")", evaluationIds.toArray());
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private List<Long> ids(String sql, Object... args) {
        return jdbc.queryForList(sql, Long.class, args);
    }

    private static Object[] prepend(long first, List<Long> rest) {
        List<Object> args = new ArrayList<>(rest.size() + 1);
        args.add(first);
        args.addAll(rest);
        return args.toArray();
    }

    private static String placeholders(List<Long> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    private static void appendIn(StringBuilder sql, List<Object> args,
                                 String column, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        sql.append(" OR ").append(column).append(" IN (").append(placeholders(ids)).append(')');
        args.addAll(ids);
    }

    private record Snapshot(List<Long> classIds, List<Long> taskIds,
                            List<Long> submissionIds, List<Long> evaluationIds) {
    }
}
