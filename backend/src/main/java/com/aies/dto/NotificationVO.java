package com.aies.dto;

import java.time.LocalDateTime;

/** 通知列表项 */
public record NotificationVO(Long id, String type, String title, String content,
                             String link, boolean read, LocalDateTime createTime) {
}
