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
            sendMessage(outputStream, SocketMessage.connected());

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while (!Thread.currentThread().isInterrupted()) {
                if (state == State.UNAUTHENTICATED
                        && System.currentTimeMillis() - connectTime > AUTH_TIMEOUT_MS) {
                    log.warn("[#{}] Auth timeout, closing connection", connectionId);
                    sendMessage(outputStream, SocketMessage.error("Auth timeout"));
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
            int nlIdx = data.indexOf('\n');
            if (nlIdx == -1) {
                if (data.length() > BUFFER_SIZE) {
                    messageBuffer.setLength(0);
                }
                return;
            }

            String raw = messageBuffer.substring(0, nlIdx).trim();
            messageBuffer.delete(0, nlIdx + 1);

            if (!raw.isEmpty()) {
                handleMessage(raw, outputStream);
            }
        }
    }

    private void handleMessage(String raw, OutputStream outputStream) throws IOException {
        SocketMessage msg = SocketMessage.parse(raw);
        if (msg == null) {
            log.warn("[#{}] Failed to parse message: {}", connectionId, raw);
            return;
        }

        try {
            switch (msg.getType()) {
                case MessageConstants.TYPE_AUTH:
                    handleAuth(msg, outputStream);
                    break;
                default:
                    if (state == State.UNAUTHENTICATED) {
                        log.warn("[#{}] Received data before auth, closing connection", connectionId);
                        sendMessage(outputStream, SocketMessage.error("Not authenticated"));
                        closeQuietly();
                        return;
                    }
                    handleAuthenticatedMessage(msg, outputStream);
                    break;
            }
        } catch (Exception e) {
            log.error("[#{}] Failed to handle message: {}", connectionId, raw, e);
        }
    }

    private void handleAuth(SocketMessage msg, OutputStream outputStream) throws IOException {
        if (state == State.AUTHENTICATED) {
            sendMessage(outputStream, SocketMessage.ok("Already authenticated"));
            return;
        }

        AuthBody authBody = msg.getBodyAs(AuthBody.class);
        String agentKey = authBody.agentKey;
        if (agentKey == null || agentKey.isEmpty()) {
            log.warn("[#{}] Empty agent key, closing connection", connectionId);
            sendMessage(outputStream, SocketMessage.error("Invalid agent key"));
            closeQuietly();
            return;
        }

        User user = userService.getByAgentKey(agentKey);
        if (user == null) {
            log.warn("[#{}] Invalid agent key, closing connection", connectionId);
            sendMessage(outputStream, SocketMessage.error("Invalid agent key"));
            closeQuietly();
            return;
        }

        if (user.getStatus() != 1) {
            log.warn("[#{}] User {} is disabled, closing connection", connectionId, user.getUsername());
            sendMessage(outputStream, SocketMessage.error("Account disabled"));
            closeQuietly();
            return;
        }

        authenticatedUserId = user.getId();
        authenticatedUsername = user.getUsername();
        state = State.AUTHENTICATED;

        socketServer.kickExistingConnection(authenticatedUserId, connectionId);
        socketServer.registerUserConnection(authenticatedUserId, connectionId);

        log.info("[#{}] Authenticated as user {} ({})", connectionId, authenticatedUserId, authenticatedUsername);
        sendMessage(outputStream, SocketMessage.ok("Authenticated"));
    }

    private void handleAuthenticatedMessage(SocketMessage msg, OutputStream outputStream) throws IOException {
        switch (msg.getType()) {
            case MessageConstants.TYPE_HOST_INFO:
                HostInfo host = MessageParser.parseHostInfo(msg.getBody());
                onHostInfoReceived(host, outputStream);
                break;
            case MessageConstants.TYPE_HOST_METRIC:
                HostMetric metric = MessageParser.parseHostMetric(msg.getBody());
                onHostMetricReceived(metric);
                break;
            default:
                log.warn("[#{}] Unknown message type: {}", connectionId, msg.getType());
                break;
        }
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

        sendMessage(outputStream, SocketMessage.okWithId("HostInfo received", registeredHostId));
    }

    private void onHostMetricReceived(HostMetric metric) {
        if (registeredHostId != null) {
            metric.setHostId(registeredHostId);
        }
        hostMetricMapper.insert(metric);
        log.debug("[#{}] Saved HostMetric: hostId={}, cpu={}%", connectionId, metric.getHostId(), metric.getCpuUsage());
    }

    private void sendMessage(OutputStream outputStream, SocketMessage msg) throws IOException {
        outputStream.write(msg.serialize().getBytes(StandardCharsets.UTF_8));
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
                SocketMessage msg = SocketMessage.command(command);
                os.write(msg.serialize().getBytes(StandardCharsets.UTF_8));
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

    private static class AuthBody {
        public String agentKey;
    }
}
