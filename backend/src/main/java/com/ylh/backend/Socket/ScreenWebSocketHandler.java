package com.ylh.backend.Socket;

import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Service.HostInfoService;
import com.ylh.backend.Service.UserService;
import com.ylh.backend.Config.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScreenWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ScreenWebSocketHandler.class);

    private final ScreenStreamManager streamManager;
    private final HostInfoService hostInfoService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public ScreenWebSocketHandler(ScreenStreamManager streamManager, HostInfoService hostInfoService,
                                   UserService userService, JwtUtil jwtUtil) {
        this.streamManager = streamManager;
        this.hostInfoService = hostInfoService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    // WebSocket 连接建立时：提取 hostId 和 token，验证权限后调用 streamManager.subscribe 订阅该主机的屏幕流
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

        Long viewerUserId = getViewerUserId(session);
        if (viewerUserId == null) {
            log.warn("WebSocket connection without valid token for hostId: {}", hostId);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        if (!checkScreenAccess(viewerUserId, host)) {
            log.warn("User {} has no access to host {} (owner userId: {})", viewerUserId, hostId, host.getUserId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Long hostUserId = host.getUserId();
        session.getAttributes().put("userId", hostUserId);

        streamManager.subscribe(hostUserId, session);
        log.info("WebSocket connected for hostId: {} (userId: {}), viewer: {}, session: {}",
                hostId, hostUserId, viewerUserId, session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    }

    // WebSocket 连接关闭时：调用 streamManager.unsubscribe 取消订阅，若为最后一个订阅者则触发 SCREEN_STOP
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

    // 屏幕查看权限检查：超管可看所有主机，组管理员可看同组主机，普通用户只能看自己的主机
    private boolean checkScreenAccess(Long viewerUserId, HostInfo host) {
        List<String> roles = userService.getUserRoleKeys(viewerUserId);
        boolean isSuperAdmin = roles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = roles.contains("GROUP_ADMIN");

        if (isSuperAdmin) {
            return true;
        }

        if (isGroupAdmin) {
            User viewer = userService.getById(viewerUserId);
            if (viewer.getGroupId() == null) {
                return false;
            }
            List<User> groupMembers = userService.list(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getGroupId, viewer.getGroupId())
            );
            List<Long> memberIds = groupMembers.stream().map(User::getId).collect(Collectors.toList());
            return memberIds.contains(host.getUserId());
        }

        return host.getUserId().equals(viewerUserId);
    }

    // 从 Session 属性中提取 JWT token 并验证，返回观看者的用户ID
    private Long getViewerUserId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object token = attrs.get("token");
        if (token instanceof String && jwtUtil.validateToken((String) token)) {
            return jwtUtil.getUserIdFromToken((String) token);
        }
        return null;
    }

    // 从 Session 属性中提取主机ID（由握手拦截器在连接时写入）
    private Long getHostId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object hostId = attrs.get("hostId");
        if (hostId instanceof Long) {
            return (Long) hostId;
        }
        return null;
    }

    // 从 Session 属性中提取主机所属用户ID（由 afterConnectionEstablished 写入）
    private Long getUserId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object userId = attrs.get("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        }
        return null;
    }
}
