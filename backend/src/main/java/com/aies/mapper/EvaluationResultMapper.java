package com.aies.mapper;

import com.aies.entity.EvaluationResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价数据访问（W7-C1 仅 SELECT 联查评价状态；评分写入属 W8 AI 评价域）。
 */
public interface EvaluationResultMapper extends BaseMapper<EvaluationResult> {

    /** 定时补偿：解析已成功但评价长时间停留 PENDING（MQ 消息丢失/消费异常） */
    @Select("""
            SELECT r.* FROM evaluation_result r
            JOIN submission s ON s.id = r.submission_id
            WHERE r.status = 'PENDING'
              AND r.update_time < #{before}
              AND s.parse_status = 'SUCCESS'
              AND s.canceled = 0
            ORDER BY r.id ASC
            LIMIT #{limit}
            """)
    List<EvaluationResult> selectStuckPending(@Param("before") LocalDateTime before,
                                              @Param("limit") int limit);

    /** 补偿投递前刷新时间戳，避免同一批 PENDING 每轮重复投递（多实例互斥） */
    @Update("""
            UPDATE evaluation_result SET update_time = #{now}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int touchPending(@Param("id") long id, @Param("now") LocalDateTime now);
}
