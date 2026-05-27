package com.ylh.backend.Socket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.HostMetric;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Mapper.HostMetricMapper;
import com.ylh.backend.Service.HostInfoService;
import com.ylh.backend.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private static final int BUFFER_SIZE = 8192;
    private static final long AUTH_TIMEOUT_MS = 30_000;

    private enum State {
        UNAUTHENTICATED, AUTHENTICATED
    }

    private final Socket clientSocket;
    private final long connectionId;
    private final UserService userService;
    private final HostInfoService hostInfoService;
    private final HostMetricMapper hostMetricMapper;
    private final SocketServer socketServer;

    private State state = State.UNAUTHENTICATED;
    private Long authenticatedUserId = null;
    private String authenticatedUsername = null;
    private Long registeredHostId = null;
    private final StringBuilder messageBuffer = new StringBuilder();
    private final AtomicLong lastActiveTime = new AtomicLong(System.currentTimeMillis());
    private final long connectTime = System.currentTimeMillis();
    private volatile OutputStream clientOutputStream = null;

    public ClientHandler(Socket clientSocket, long connectionId,
                         UserService userService, HostInfoService hostInfoService,
                         HostMetricMapper hostMetricMapper, SocketServer socketServer) {
        this.clientSocket = clientSocket;
        this.connectionId = connectionId;
        this.userService = userService;
        this.hostInfoService = hostInfoService;
        this.hostMetricMapper = hostMetricMapper;
        this.socketServer = socketServer;
    }

    @Override
    public void run() {
        String clientAddress = clientSocket.getRemoteSocketAddress().toString();
        log.info("[#{}] Client connected: {}", connectionId, clientAddress);

        try (InputStream inputStream = clientSocket.getInputStream();
             OutputStream outputStream = clientSocket.getOutputStream()) {

            clientOutputStream = outputStream;
            sendResponse(outputStream, "CONNECTED:HostMonitor Server v1.0");

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while (!Thread.currentThread().isInterrupted()) {
                if (state == State.UNAUTHENTICATED
                        && System.currentTimeMillis() - connectTime > AUTH_TIMEOUT_MS) {
                    log.warn("[#{}] Auth timeout, closing connection", connectionId);
                    sendResponse(outputStream, "ERROR:Auth timeout");
                    break;
                }

                try {
                    bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }
                    lastActiveTime.set(System.currentTimeMillis());
                    String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    messageBuffer.append(chunk);
                    processBuffer(outputStream);
                } catch (SocketTimeoutException e) {
                    continue;
                }
            }
        } catch (IOException e) {
            log.info("[#{}] Client disconnected: {}", connectionId, e.getMessage());
        } finally {
            clientOutputStream = null;
            cleanupConnection();
            closeQuietly();
            log.info("[#{}] Handler finished for: {}", connectionId, clientAddress);
        }
    }

    private void cleanupConnection() {
        socketServer.removeUserConnection(authenticatedUserId, connectionId);
    }

    private void processBuffer(OutputStream outputStream) throws IOException {
        while (true) {
            String data = messageBuffer.toString();
            int startIndex = findMessageStart(data);
            if (startIndex == -1) {
                if (data.length() > BUFFER_SIZE) {
                    messageBuffer.setLength(0);
                }
                return;
            }

            if (startIndex > 0) {
                messageBuffer.delete(0, startIndex);
                data = messageBuffer.toString();
            }

            int nextStart = findNextMessageStart(data);
            if (nextStart == -1) {
                int nlIdx = data.indexOf('\n');
                if (nlIdx != -1) {
                    String message = messageBuffer.substring(0, nlIdx);
                    messageBuffer.delete(0, nlIdx + 1);
                    handleMessage(message.trim(), outputStream);
                }
                return;
            }

            String message = messageBuffer.substring(0, nextStart);
            messageBuffer.delete(0, nextStart);

            handleMessage(message.trim(), outputStream);
        }
    }

    private int findMessageStart(String data) {
        int authIdx = data.indexOf("AUTH:");
        int hostInfoIdx = data.indexOf("HostInfo{");
        int hostMetricIdx = data.indexOf("HostMetric{");

        int min = -1;
        if (authIdx != -1) min = authIdx;
        if (hostInfoIdx != -1 && (min == -1 || hostInfoIdx < min)) min = hostInfoIdx;
        if (hostMetricIdx != -1 && (min == -1 || hostMetricIdx < min)) min = hostMetricIdx;
        return min;
    }

    private int findNextMessageStart(String data) {
        int searchFrom = 9;
        int authIdx = data.indexOf("AUTH:", searchFrom);
        int hostInfoIdx = data.indexOf("HostInfo{", searchFrom);
        int hostMetricIdx = data.indexOf("HostMetric{", searchFrom);

        int min = -1;
        if (authIdx != -1) min = authIdx;
        if (hostInfoIdx != -1 && (min == -1 || hostInfoIdx < min)) min = hostInfoIdx;
        if (hostMetricIdx != -1 && (min == -1 || hostMetricIdx < min)) min = hostMetricIdx;
        return min;
    }

    private void handleMessage(String message, OutputStream outputStream) throws IOException {
        try {
            if (message.startsWith("AUTH:")) {
                handleAuth(message, outputStream);
            } else if (state == State.UNAUTHENTICATED) {
                log.warn("[#{}] Received data before auth, closing connection", connectionId);
                sendResponse(outputStream, "ERROR:Not authenticated");
                closeQuietly();
            } else if (MessageParser.isHostInfoMessage(message)) {
                HostInfo host = MessageParser.parseHostInfo(message);
                onHostInfoReceived(host, outputStream);
            } else if (MessageParser.isHostMetricMessage(message)) {
                HostMetric metric = MessageParser.parseHostMetric(message);
                onHostMetricReceived(metric);
            } else {
                log.warn("[#{}] Unknown message type: {}", connectionId, message);
            }
        } catch (Exception e) {
            log.error("[#{}] Failed to handle message: {}", connectionId, message, e);
        }
    }

    private void handleAuth(String message, OutputStream outputStream) throws IOException {
        if (state == State.AUTHENTICATED) {
            sendResponse(outputStream, "OK:Already authenticated");
            return;
        }

        String agentKey = message.substring(5).trim();
        if (agentKey.isEmpty()) {
            log.warn("[#{}] Empty agent key, closing connection", connectionId);
            sendResponse(outputStream, "ERROR:Invalid agent key");
            closeQuietly();
            return;
        }

        User user = userService.getByAgentKey(agentKey);
        if (user == null) {
            log.warn("[#{}] Invalid agent key, closing connection", connectionId);
            sendResponse(outputStream, "ERROR:Invalid agent key");
            closeQuietly();
            return;
        }

        if (user.getStatus() != 1) {
            log.warn("[#{}] User {} is disabled, closing connection", connectionId, user.getUsername());
            sendResponse(outputStream, "ERROR:Account disabled");
            closeQuietly();
            return;
        }

        authenticatedUserId = user.getId();
        authenticatedUsername = user.getUsername();
        state = State.AUTHENTICATED;

        socketServer.kickExistingConnection(authenticatedUserId, connectionId);
        socketServer.registerUserConnection(authenticatedUserId, connectionId);

        log.info("[#{}] Authenticated as user {} ({})", connectionId, authenticatedUserId, authenticatedUsername);
        sendResponse(outputStream, "OK:Authenticated");
    }

    private void onHostInfoReceived(HostInfo host, OutputStream outputStream) throws IOException {
        host.setUserId(authenticatedUserId);

        HostInfo existing = hostInfoService.getOne(
                new LambdaQueryWrapper<HostInfo>()
                        .eq(HostInfo::getUserId, authenticatedUserId)
        );

        if (existing != null) {
            registeredHostId = existing.getId();
            existing.setHostname(host.getHostname());
            existing.setIpAddress(host.getIpAddress());
            existing.setCpuModel(host.getCpuModel());
            existing.setCpuCores(host.getCpuCores());
            existing.setMemoryTotal(host.getMemoryTotal());
            existing.setDiskTotal(host.getDiskTotal());
            existing.setOsName(host.getOsName());
            existing.setOsVersion(host.getOsVersion());
            existing.setStatus(1);
            existing.setLastHeartbeat(LocalDateTime.now());
            hostInfoService.updateById(existing);
            log.info("[#{}] Updated host: id={}, hostname={}", connectionId, existing.getId(), existing.getHostname());
        } else {
            host.setStatus(1);
            host.setLastHeartbeat(LocalDateTime.now());
            hostInfoService.save(host);
            registeredHostId = host.getId();
            log.info("[#{}] Registered new host: id={}, hostname={}", connectionId, host.getId(), host.getHostname());
        }

        sendResponse(outputStream, "OK:HostInfo received,id=" + registeredHostId);
    }

    private void onHostMetricReceived(HostMetric metric) {
        if (registeredHostId != null) {
            metric.setHostId(registeredHostId);
        }
        hostMetricMapper.insert(metric);
        log.debug("[#{}] Saved HostMetric: hostId={}, cpu={}%", connectionId, metric.getHostId(), metric.getCpuUsage());
    }

    private void sendResponse(OutputStream outputStream, String response) throws IOException {
        outputStream.write((response + "\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    public long getLastActiveTime() {
        return lastActiveTime.get();
    }

    public long getConnectionId() {
        return connectionId;
    }

    public boolean isAuthenticated() {
        return state == State.AUTHENTICATED;
    }

    public Long getAuthenticatedUserId() {
        return authenticatedUserId;
    }

    public void closeQuietly() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public void sendCommand(String command) {
        OutputStream os = clientOutputStream;
        if (os == null) {
            log.warn("[#{}] Cannot send command '{}', no output stream", connectionId, command);
            return;
        }
        try {
            synchronized (os) {
                os.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (IOException e) {
            log.warn("[#{}] Failed to send command '{}': {}", connectionId, command, e.getMessage());
        }
    }

    public String getClientAddress() {
        if (clientSocket == null || clientSocket.isClosed()) {
            return "disconnected";
        }
        return clientSocket.getRemoteSocketAddress().toString();
    }
}
