package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt模板版本（V4 §3.7；红线R1：代码中禁止业务Prompt字面量，一律走本表）。
 *
 * <p>enabled=1 全局唯一启用版（应用层保证）；占位符集 {metrics}/{parsed_text}，
 * PromptAssembler 仅做替换与长度裁剪。</p>
 */
@Data
@TableName("prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板版本号（evaluation_history 经 version_no 逻辑关联） */
    private Integer versionNo;

    /** 系统Prompt模板（含指标/作业内容占位符） */
    private String content;

    /** 1=启用（全局唯一） */
    private Integer enabled;

    private LocalDateTime createTime;
}
