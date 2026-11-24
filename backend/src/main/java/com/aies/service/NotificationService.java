package com.aies.service;

import com.aies.dto.NotificationVO;
import com.aies.entity.Notification;
import com.aies.mapper.NotificationMapper;
import com.aies.realtime.RealtimeWebSocketHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量通知中心：落库 + WebSocket 推送（notification.created）。
 *
 * <p>通知属于旁路能力：任何写入/推送异常都只记日志，绝不向主业务抛出。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int MAX_PAGE_SIZE = 100;

    private final NotificationMapper notificationMapper;
    private final RealtimeWebSocketHandler webSocketHandler;

    /** 写一条通知并实时推送未读数（接收方在线时角标即时更新） */
    public void notify(long userId, String type, String title, String content, String link) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content == null ? "" : content);
            notification.setLink(link);
            notification.setReadFlag(0);
            notification.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(notification);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", notification.getId());
            payload.put("type", type);
            payload.put("title", title);
            payload.put("content", notification.getContent());
            payload.put("link", link);
            payload.put("unread", notificationMapper.countUnread(userId));
            payload.put("createTime", notification.getCreateTime());
            webSocketHandler.sendToUser(userId, "notification.created", payload);
        } catch (Exception e) {
            log.warn("通知写入/推送失败（不影响主业务）: userId={} type={} err={}",
                    userId, type, e.getMessage());
        }
    }

    public List<NotificationVO> list(long userId, int size) {
        int limit = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<Notification> rows = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT " + limit));
        return rows.stream().map(this::toVO).toList();
    }

    public long unreadCount(long userId) {
        return notificationMapper.countUnread(userId);
    }

    /** 单条已读（仅本人通知，越权/不存在静默为 0 行） */
    public int markRead(long userId, long id) {
        return notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0)
                .set(Notification::getReadFlag, 1));
    }

    public int markAllRead(long userId) {
        return notificationMapper.markAllRead(userId);
    }

    private NotificationVO toVO(Notification notification) {
        return new NotificationVO(notification.getId(), notification.getType(),
                notification.getTitle(), notification.getContent(), notification.getLink(),
                Integer.valueOf(1).equals(notification.getReadFlag()),
                notification.getCreateTime());
    }
}
