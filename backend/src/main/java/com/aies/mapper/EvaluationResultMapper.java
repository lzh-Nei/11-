package com.aies.mapper;

import com.aies.entity.EvaluationResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 评价数据访问（W7-C1 仅 SELECT 联查评价状态；评分写入属 W8 AI 评价域）。
 */
public interface EvaluationResultMapper extends BaseMapper<EvaluationResult> {
}
