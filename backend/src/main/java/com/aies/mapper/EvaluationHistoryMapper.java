package com.aies.mapper;

import com.aies.entity.EvaluationHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 评价历史版本（ADR-004只增不改不删：仅insert+select，无update/delete接口） */
public interface EvaluationHistoryMapper extends BaseMapper<EvaluationHistory> {
}
