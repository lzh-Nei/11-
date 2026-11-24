package com.aies.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线聊天 DTO（V20）：会话摘要、消息视图、发送载荷与已读结果。
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    /** 会话列表项：对端信息 + 最后一条消息 + 未读数 + 在线状态 */
    public record ContactVO(long userId, String realName, String role, boolean online,
                            String lastContent, LocalDateTime lastTime, long unread) {
    }

    /** 消息视图 */
    public record MessageVO(long id, long senderId, long receiverId, String content,
                            boolean read, LocalDateTime createTime) {
    }

    /** 发送载荷 */
    public record SendRequest(
            @NotNull(message = "接收人不能为空") @Positive(message = "接收人非法") Long receiverId,
            @NotBlank(message = "消息内容不能为空") @Size(max = 1000, message = "消息不能超过1000字")
            String content) {
    }

    /** 已读结果 */
    public record ReadResult(int updated) {
    }

    /** 按发送人聚合的未读数（MyBatis 映射用，非对外契约） */
    @Data
    public static class UnreadRow {
        private Long senderId;
        private Long unread;
    }
}
