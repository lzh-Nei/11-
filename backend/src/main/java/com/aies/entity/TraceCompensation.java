package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 留痕补偿行（V6__trace_compensation.sql，LLD §4.3步骤6补偿日志方案落地）。
 *
 * <p>TraceRecorder冷桶写失败→本表入队（payload=留痕JSON原文）；
 * TraceCompensationService定时扫描PENDING且到期行→storeCold补写→DONE。</p>
 */
@Data
@TableName("trace_compensation")
public class TraceCompensation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 评价traceId（uk_comp_trace唯一） */
    private String traceId;

    /** 冷桶对象key（trace/{traceId}.json） */
    private String objectKey;

    /** 留痕JSON原文（补写用） */
    private String payload;

    /** 字典：PENDING/DONE */
    private String status;

    /** 补写重试次数 */
    private Integer retryCount;

    /** 最近一次失败原因（截断512） */
    private String lastError;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
