package com.aies.dto;

import com.aies.entity.Task;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务视图（T-04 详情 / T-05 列表）。
 *
 * <p>UC-13：服务端按角色裁剪——submissionCount 仅教师/管理员视角装配，学生VO不含。</p>
 */
@Data
public class TaskVO {

    private Long id;
    private String title;
    private String description;
    private Long teacherId;
    private Long classId;
    private LocalDateTime deadline;
    private String status;
    private LocalDateTime createTime;
    private List<TaskMetricVO> metrics;

    /** 提交统计（教师/管理员视角专有；学生不装配） */
    private Long submissionCount;

    public static TaskVO from(Task task, List<TaskMetricVO> metrics) {
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setTeacherId(task.getTeacherId());
        vo.setClassId(task.getClassId());
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setCreateTime(task.getCreateTime());
        vo.setMetrics(metrics);
        return vo;
    }
}
