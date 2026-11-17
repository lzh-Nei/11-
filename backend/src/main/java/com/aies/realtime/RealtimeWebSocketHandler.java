package com.aies.realtime;

import com.aies.entity.User;
import com.aies.mapper.UserMapper;
import com.aies.security.JwtService;
import com.aies.security.LoginPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评价状态实时通道（WebSocket）。
 *
 * <p>握手时用 query token 校验 JWT（浏览器 WebSocket 无法方便设置 Authorization 头），
 * 无效/已登出/账号禁用直接 1008 关闭；连接按 userId 注册。客户端每 30 秒发 ping，
 * 服务端回 pong；断线重连由前端指数退避负责。当前单实例会话表，跨实例扩展时可替换为
 * Redis Pub/Sub + 网关路由（对应课件 P17）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = UriComponentsBuilder.fromUri(session.getUri())
                .build().getQueryParams().getFirst("token");
        try {
            LoginPrincipal principal = jwtService.parseAccessToken(token);
            if (jwtService.isAccessTokenBlacklisted(principal.jti())) {
                throw new IllegalArgumentException("token revoked");
            }
            User user = userMapper.selectById(principal.userId());
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                throw new IllegalArgumentException("account disabled");
            }
            session.getAttributes().put("userId", principal.userId());
            sessionsByUser.computeIfAbsent(principal.userId(), k -> ConcurrentHashMap.newKeySet())
                    .add(session);
            send(session, Map.of("type", "connected", "userId", principal.userId()));
        } catch (Exception e) {
            log.debug("WebSocket 鉴权失败: {}", e.getMessage());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(),
                new TypeReference<>() {
                });
        if ("ping".equals(payload.get("type"))) {
            send(session, Map.of("type", "pong", "ts", System.currentTimeMillis()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket 传输异常: {}", exception.getMessage());
        removeSession(session);
    }

    /** 向用户的所有在线标签页推送事件 */
    public void sendToUser(long userId, String type, Object data) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", type,
                "data", data,
                "ts", System.currentTimeMillis());
        sessions.forEach(session -> send(session, payload));
    }

    private void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            removeSession(session);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.debug("WebSocket 推送失败，移除会话: {}", e.getMessage());
            removeSession(session);
        }
    }

    private void removeSession(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long id) {
            Set<WebSocketSession> sessions = sessionsByUser.get(id);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByUser.remove(id);
                }
            }
        }
    }
}
