package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线聊天消息（V20）：学生 ↔ 任课教师一对一文本消息。
 *
 * <p>表 chat_message；发送人与接收人可达性由 ChatService 依据 class.teacher_id 校验。</p>
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long senderId;

    private Long receiverId;

    /** 文本内容（1-1000 字） */
    private String content;

    /** 0 未读，1 已读 */
    private Integer readFlag;

    private LocalDateTime createTime;
}
