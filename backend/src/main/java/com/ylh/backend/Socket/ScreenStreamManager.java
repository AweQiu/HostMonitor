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

    public interface ScreenSubscriptionListener {
        void onFirstSubscriber(Long userId);
        void onLastSubscriberLeft(Long userId);
    }

    public void setSubscriptionListener(ScreenSubscriptionListener listener) {
        this.subscriptionListener = listener;
    }

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

    public boolean hasSubscribers(Long userId) {
        List<WebSocketSession> sessions = subscribers.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

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
