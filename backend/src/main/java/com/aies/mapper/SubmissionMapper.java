package com.aies.mapper;

import com.aies.entity.Submission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提交数据访问（基础CRUD由 MyBatis-Plus BaseMapper 提供）。
 */
public interface SubmissionMapper extends BaseMapper<Submission> {

    /** 版本号自增依据（uk_task_student_version 唯一键兜底并发重复） */
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM submission "
            + "WHERE task_id = #{taskId} AND student_id = #{studentId}")
    int selectMaxVersion(@Param("taskId") long taskId, @Param("studentId") long studentId);
}
