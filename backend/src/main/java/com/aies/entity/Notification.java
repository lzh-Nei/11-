package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（V2）：评价状态、教师复核、申诉受理与聊天消息的轻量通知中心。
 *
 * <p>表 notification；写入失败只记日志，不影响主业务链路。</p>
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 sys_user.id */
    private Long userId;

    /** EVALUATION / REVIEW / APPEAL / CHAT / SYSTEM */
    private String type;

    private String title;

    private String content;

    /** 前端跳转路径（可为空） */
    private String link;

    /** 0 未读，1 已读 */
    private Integer readFlag;

    private LocalDateTime createTime;
}
