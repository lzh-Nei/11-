package com.aies.mapper;

import com.aies.entity.TraceCompensation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 留痕补偿Mapper（V6；仅insert/select/updateById补写状态流转）。
 */
@Mapper
public interface TraceCompensationMapper extends BaseMapper<TraceCompensation> {
}
