package com.aies.mapper;

import com.aies.entity.LlmConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** LLM运行参数（单行表）；R4红线：timeout/max_retry/temperature/model一律本表读取 */
public interface LlmConfigMapper extends BaseMapper<LlmConfig> {
}
