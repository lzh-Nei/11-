package com.aies.model;

import lombok.Data;

/**
 * 班级学生数投影（报表提交率分母：role=STUDENT 且启用的用户数）。
 */
@Data
public class ClassStudentCount {

    private Long classId;

    private int studentCount;
}
