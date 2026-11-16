package com.aies.dto;

import lombok.Data;

/**
 * 任务所属班级与授课教师展示信息（列表批量装配，避免逐条查询）。
 */
@Data
public class TaskOwnerVO {

    private Long taskId;
    private String className;
    private String teacherName;
}
