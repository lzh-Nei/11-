package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业任务（03-数据库设计 §3.3 / V2__org_task.sql）。
 *
 * <p>status 字典：DRAFT/PUBLISHED/CLOSED（ENUM→VARCHAR翻译规则，应用层枚举校验）。
 * 发布后指标集锁定由应用层拦截（UC-11）；deadline 早于当前→30006（应用层校验，UC-10 E2）。</p>
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务标题 */
    private String title;

    /** 任务描述 */
    private String description;

    /** 发布教师（创建即归属，S-05：教师视角仅本人 task 可见） */
    private Long teacherId;

    /** 目标班级 */
    private Long classId;

    /** 截止时间（早于当前→30006，应用层校验） */
    private LocalDateTime deadline;

    /** 字典：DRAFT/PUBLISHED/CLOSED */
    private String status;

    /** 发布时间（发布动作发生时刻；草稿为空） */
    private LocalDateTime publishTime;

    private LocalDateTime createTime;
}
