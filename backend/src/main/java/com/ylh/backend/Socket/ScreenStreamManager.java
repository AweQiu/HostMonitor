package com.ylh.backend.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ScreenStreamManager {

    private static final Logger log = LoggerFactory.getLogger(ScreenStreamManager.class);

    private final Map<Long, byte[]> latestFrames = new ConcurrentHashMap<>();
    private final Map<Long, List<WebSocketSession>> subscribers = new ConcurrentHashMap<>();
    private final Set<Long> activeStreams = ConcurrentHashMap.newKeySet();

    private volatile ScreenSubscriptionListener subscriptionListener;

    // 订阅监听器接口：第一个订阅者到达时回调 onFirstSubscriber（触发 SCREEN_START），最后一个离开时回调 onLastSubscriberLeft（触发 SCREEN_STOP）
    public interface ScreenSubscriptionListener {
        void onFirstSubscriber(Long userId);
        void onLastSubscriberLeft(Long userId);
    }

    public void setSubscriptionListener(ScreenSubscriptionListener listener) {
        this.subscriptionListener = listener;
    }

    // 接收 UDP 帧数据，缓存最新帧并广播给该用户的所有 WebSocket 订阅者；无订阅者时丢弃帧以节省资源
    public boolean updateFrame(Long userId, byte[] jpegData) {
        if (userId == null || jpegData == null || jpegData.length == 0) {
            return false;
        }

        List<WebSocketSession> sessions = subscribers.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }

        latestFrames.put(userId, jpegData);
        activeStreams.add(userId);
        broadcastToSubscribers(userId, jpegData, sessions);
        return true;
    }

    // 判断该用户是否有 WebSocket 订阅者，用于 UDP 帧接收时决定是否丢弃
    public boolean hasSubscribers(Long userId) {
        List<WebSocketSession> sessions = subscribers.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    // 将 WebSocket 会话注册为该用户屏幕流的订阅者；如有缓存帧则立即发送（快速首帧）；若是第一个订阅者则触发 onFirstSubscriber 回调
    public void subscribe(Long userId, WebSocketSession session) {
        boolean wasEmpty = !hasSubscribers(userId);

        subscribers.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
        log.info("WebSocket session {} subscribed to user {} screen stream", session.getId(), userId);

        byte[] latestFrame = latestFrames.get(userId);
        if (latestFrame != null) {
            try {
                session.sendMessage(new BinaryMessage(latestFrame));
            } catch (IOException e) {
                log.warn("Failed to send latest frame to session {}: {}", session.getId(), e.getMessage());
            }
        }

        if (wasEmpty && subscriptionListener != null) {
            subscriptionListener.onFirstSubscriber(userId);
        }
    }

    // 将 WebSocket 会话从订阅列表移除；若是最后一个订阅者离开则触发 onLastSubscriberLeft 回调，并清理缓存帧
    public void unsubscribe(Long userId, WebSocketSession session) {
        List<WebSocketSession> sessions = subscribers.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                subscribers.remove(userId);
                activeStreams.remove(userId);

                if (subscriptionListener != null) {
                    subscriptionListener.onLastSubscriberLeft(userId);
                }
            }
        }
        log.info("WebSocket session {} unsubscribed from user {} screen stream", session.getId(), userId);
    }

    public void removeUserFrames(Long userId) {
        latestFrames.remove(userId);
        activeStreams.remove(userId);
    }

    public Set<Long> getActiveStreams() {
        return activeStreams;
    }

    // 将 JPEG 帧以 BinaryMessage 广播给该用户的所有在线 WebSocket 订阅者
    private void broadcastToSubscribers(Long userId, byte[] jpegData, List<WebSocketSession> sessions) {
        BinaryMessage message = new BinaryMessage(jpegData);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Failed to send frame to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }
}
