package com.ylh.backend.Socket;

import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Service.HostInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.Map;

public class ScreenWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ScreenWebSocketHandler.class);

    private final ScreenStreamManager streamManager;
    private final HostInfoService hostInfoService;

    public ScreenWebSocketHandler(ScreenStreamManager streamManager, HostInfoService hostInfoService) {
        this.streamManager = streamManager;
        this.hostInfoService = hostInfoService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long hostId = getHostId(session);
        if (hostId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        HostInfo host = hostInfoService.getById(hostId);
        if (host == null) {
            log.warn("WebSocket connection for unknown hostId: {}", hostId);
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Long userId = host.getUserId();
        session.getAttributes().put("userId", userId);

        streamManager.subscribe(userId, session);
        log.info("WebSocket connected for hostId: {} (userId: {}), session: {}", hostId, userId, session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserId(session);
        if (userId != null) {
            streamManager.unsubscribe(userId, session);
        }
        log.info("WebSocket disconnected, userId: {}, session: {}, status: {}", userId, session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long userId = getUserId(session);
        log.warn("WebSocket transport error, userId: {}, session: {}: {}", userId, session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private Long getHostId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object hostId = attrs.get("hostId");
        if (hostId instanceof Long) {
            return (Long) hostId;
        }
        return null;
    }

    private Long getUserId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object userId = attrs.get("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        }
        return null;
    }
}
