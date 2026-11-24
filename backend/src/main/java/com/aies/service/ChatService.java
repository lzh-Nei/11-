package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.chat.ChatDtos.ContactVO;
import com.aies.dto.chat.ChatDtos.MessageVO;
import com.aies.dto.chat.ChatDtos.UnreadRow;
import com.aies.entity.ChatMessage;
import com.aies.entity.SysClass;
import com.aies.entity.User;
import com.aies.mapper.ChatMessageMapper;
import com.aies.mapper.SysClassMapper;
import com.aies.mapper.UserMapper;
import com.aies.realtime.RealtimeWebSocketHandler;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线聊天（V20）：学生 ↔ 任课教师一对一文本消息。
 *
 * <p>可达性规则：学生只能联系本班 class.teacher_id 对应教师；教师只能联系自己班级的学生。
 * 服务端在会话列表、历史、发送、已读四处统一校验，越权统一 40301。
 * 新消息先落库，再经 {@link RealtimeWebSocketHandler} 推送给接收方与发送方其他标签页。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    static final String ROLE_STUDENT = "STUDENT";
    static final String ROLE_TEACHER = "TEACHER";
    static final int MAX_CONTENT_LENGTH = 1000;
    static final int MAX_PAGE_SIZE = 50;

    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final SysClassMapper classMapper;
    private final RealtimeWebSocketHandler webSocketHandler;
    private final NotificationService notificationService;

    /** 会话列表：允许联系的对端 + 最后消息 + 未读数 + 在线状态 */
    public List<ContactVO> contacts(LoginPrincipal principal) {
        User me = requireUser(principal.userId());
        List<User> peers = allowedPeers(me, principal.role());
        if (peers.isEmpty()) {
            return List.of();
        }
        Map<Long, ChatMessage> latest = new HashMap<>();
        for (ChatMessage message : chatMessageMapper.selectLatestPerPeer(me.getId())) {
            latest.put(peerOf(message, me.getId()), message);
        }
        Map<Long, Long> unread = new HashMap<>();
        for (UnreadRow row : chatMessageMapper.selectUnreadBySender(me.getId())) {
            if (row.getSenderId() != null) {
                unread.put(row.getSenderId(), row.getUnread() == null ? 0L : row.getUnread());
            }
        }
        String peerRole = ROLE_STUDENT.equals(principal.role()) ? ROLE_TEACHER : ROLE_STUDENT;
        return peers.stream()
                .map(peer -> {
                    ChatMessage last = latest.get(peer.getId());
                    return new ContactVO(peer.getId(), peer.getRealName(), peerRole,
                            webSocketHandler.isOnline(peer.getId()),
                            last == null ? null : last.getContent(),
                            last == null ? null : last.getCreateTime(),
                            unread.getOrDefault(peer.getId(), 0L));
                })
                .sorted(Comparator.comparing(ContactVO::lastTime,
                                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                        .thenComparing(ContactVO::realName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /** 历史消息：按 id 倒序取一页后翻转为时间正序；beforeId 用于加载更早消息 */
    public List<MessageVO> messages(LoginPrincipal principal, long peerId, Long beforeId, int size) {
        User me = requireUser(principal.userId());
        requirePeer(me, principal.role(), peerId);
        int limit = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<>();
        query.and(w -> w
                .and(x -> x.eq(ChatMessage::getSenderId, me.getId())
                        .eq(ChatMessage::getReceiverId, peerId))
                .or(x -> x.eq(ChatMessage::getSenderId, peerId)
                        .eq(ChatMessage::getReceiverId, me.getId())));
        if (beforeId != null) {
            query.lt(ChatMessage::getId, beforeId);
        }
        query.orderByDesc(ChatMessage::getId).last("LIMIT " + limit);
        List<ChatMessage> rows = new ArrayList<>(chatMessageMapper.selectList(query));
        Collections.reverse(rows);
        return rows.stream().map(this::toVO).toList();
    }

    /** 发送消息：校验可达性 → 落库 → WebSocket 推送给双方 */
    public MessageVO send(LoginPrincipal principal, long receiverId, String content) {
        User me = requireUser(principal.userId());
        requirePeer(me, principal.role(), receiverId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息内容需为1-1000字");
        }
        ChatMessage message = new ChatMessage();
        message.setSenderId(me.getId());
        message.setReceiverId(receiverId);
        message.setContent(text);
        message.setReadFlag(0);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);

        MessageVO vo = toVO(message);
        webSocketHandler.sendToUser(receiverId, "chat.message", vo);
        // 发送方其他标签页同步；前端按消息 id 去重
        webSocketHandler.sendToUser(me.getId(), "chat.message", vo);
        // 通知中心留痕（角标/列表），失败不影响消息送达
        String preview = text.length() > 60 ? text.substring(0, 60) + "…" : text;
        notificationService.notify(receiverId, "CHAT", "收到新消息", preview, "/messages");
        log.info("聊天消息已发送: from={} to={} id={}", me.getId(), receiverId, message.getId());
        return vo;
    }

    /** 打开会话时把对端发来的未读消息置为已读，返回更新条数 */
    public int markRead(LoginPrincipal principal, long peerId) {
        User me = requireUser(principal.userId());
        requirePeer(me, principal.role(), peerId);
        return chatMessageMapper.markRead(me.getId(), peerId);
    }

    private List<User> allowedPeers(User me, String role) {
        if (ROLE_STUDENT.equals(role)) {
            if (me.getClassId() == null) {
                return List.of();
            }
            SysClass clazz = classMapper.selectById(me.getClassId());
            if (clazz == null || clazz.getTeacherId() == null) {
                return List.of();
            }
            User teacher = userMapper.selectById(clazz.getTeacherId());
            return teacher != null && Integer.valueOf(1).equals(teacher.getStatus())
                    ? List.of(teacher) : List.of();
        }
        if (ROLE_TEACHER.equals(role)) {
            List<SysClass> classes = classMapper.selectList(new LambdaQueryWrapper<SysClass>()
                    .eq(SysClass::getTeacherId, me.getId()));
            if (classes.isEmpty()) {
                return List.of();
            }
            Set<Long> classIds = classes.stream().map(SysClass::getId).collect(Collectors.toSet());
            return userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getClassId, classIds)
                    .eq(User::getStatus, 1)
                    .orderByAsc(User::getRealName));
        }
        return List.of();
    }

    private void requirePeer(User me, String role, long peerId) {
        boolean allowed = allowedPeers(me, role).stream()
                .anyMatch(peer -> peer.getId() != null && peer.getId() == peerId);
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private User requireUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private long peerOf(ChatMessage message, long me) {
        return message.getSenderId() == me ? message.getReceiverId() : message.getSenderId();
    }

    private MessageVO toVO(ChatMessage message) {
        return new MessageVO(message.getId(), message.getSenderId(), message.getReceiverId(),
                message.getContent(), Integer.valueOf(1).equals(message.getReadFlag()),
                message.getCreateTime());
    }
}
