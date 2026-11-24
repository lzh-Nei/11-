package com.aies.mapper;

import com.aies.entity.Submission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交数据访问（基础CRUD由 MyBatis-Plus BaseMapper 提供）。
 */
public interface SubmissionMapper extends BaseMapper<Submission> {

    /** 版本号自增依据（uk_task_student_version 唯一键兜底并发重复） */
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM submission "
            + "WHERE task_id = #{taskId} AND student_id = #{studentId}")
    int selectMaxVersion(@Param("taskId") long taskId, @Param("studentId") long studentId);

    /** 定时补偿：长时间停留 PENDING（消息丢失）或 QUEUED（消费中断）的提交 */
    @Select("""
            SELECT * FROM submission
            WHERE canceled = 0
              AND ((parse_status = 'PENDING' AND create_time < #{pendingBefore})
                OR (parse_status = 'QUEUED' AND create_time < #{queuedBefore}))
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<Submission> selectStuckParses(@Param("pendingBefore") LocalDateTime pendingBefore,
                                       @Param("queuedBefore") LocalDateTime queuedBefore,
                                       @Param("limit") int limit);
}
