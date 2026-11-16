package com.aies.mapper;

import com.aies.entity.PromptTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** Prompt模板版本（R1红线：业务Prompt唯一来源，评价管道取 enabled=1 启用版） */
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
