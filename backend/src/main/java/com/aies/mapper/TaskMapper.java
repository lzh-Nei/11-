package com.aies.mapper;

import com.aies.entity.Task;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 任务数据访问（基础CRUD由 MyBatis-Plus BaseMapper 提供）。
 */
public interface TaskMapper extends BaseMapper<Task> {

    /** 班级归属校验用（S-05：教师创建任务须绑定本人班级，UC-10前置） */
    @Select("SELECT teacher_id FROM class WHERE id = #{classId} LIMIT 1")
    Long selectClassTeacher(@Param("classId") Long classId);

    /** 班级码→班级ID（UC-43注册绑定，A-08；无效→30011） */
    @Select("SELECT id FROM class WHERE class_code = #{classCode} LIMIT 1")
    Long selectClassIdByCode(@Param("classCode") String classCode);

    /** 教师视角提交统计（UC-13：详情含提交数；submission 表 V3 迁移） */
    @Select("SELECT COUNT(*) FROM submission WHERE task_id = #{taskId}")
    long countSubmissions(@Param("taskId") Long taskId);
}
