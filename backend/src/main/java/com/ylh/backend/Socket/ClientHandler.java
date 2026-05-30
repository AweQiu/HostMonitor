package com.ylh.backend.Socket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.HostMetric;
import com.ylh.backend.Entity.HostProcess;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Mapper.HostMetricMapper;
import com.ylh.backend.Service.AlertService;
import com.ylh.backend.Service.HostInfoService;
import com.ylh.backend.Service.HostProcessService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024;
    private static final long AUTH_TIMEOUT_MS = 30_000;

    private enum State {
        UNAUTHENTICATED, AUTHENTICATED
    }

    private final Socket clientSocket;
    private final long connectionId;
    private final UserService userService;
    private final HostInfoService hostInfoService;
    private final HostMetricMapper hostMetricMapper;
    private final HostProcessService hostProcessService;
    private final AlertService alertService;
    private final SocketServer socketServer;

    private State state = State.UNAUTHENTICATED;
    private Long authenticatedUserId = null;
    private String authenticatedUsername = null;
    private Long registeredHostId = null;
    private final StringBuilder messageBuffer = new StringBuilder();
    private final AtomicLong lastActiveTime = new AtomicLong(System.currentTimeMillis());
    private final long connectTime = System.currentTimeMillis();
    private volatile OutputStream clientOutputStream = null;

    private final Map<Integer, HostProcess> processSnapshot = new HashMap<>();
    private int expectedProcessSeq = 1;
    private volatile long lastHeartbeatDbUpdate = 0;
    private volatile long lastPerfAlertCheck = 0;

    public ClientHandler(Socket clientSocket, long connectionId,
                         UserService userService, HostInfoService hostInfoService,
                         HostMetricMapper hostMetricMapper, HostProcessService hostProcessService,
                         AlertService alertService, SocketServer socketServer) {
        this.clientSocket = clientSocket;
        this.connectionId = connectionId;
        this.userService = userService;
        this.hostInfoService = hostInfoService;
        this.hostMetricMapper = hostMetricMapper;
        this.hostProcessService = hostProcessService;
        this.alertService = alertService;
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
                if (data.length() > MAX_MESSAGE_SIZE) {
                    log.warn("[#{}] Message buffer exceeded max size ({}), clearing", connectionId, MAX_MESSAGE_SIZE);
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

        socketServer.notifyClientConnected(authenticatedUserId);
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
            case MessageConstants.TYPE_PROCESS_LIST:
                List<HostProcess> processes = MessageParser.parseProcessList(msg.getBody());
                onProcessListReceived(processes);
                break;
            case MessageConstants.TYPE_PROCESS_LIST_FULL:
                onProcessListFullReceived(msg, outputStream);
                break;
            case MessageConstants.TYPE_PROCESS_LIST_DELTA:
                onProcessListDeltaReceived(msg, outputStream);
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

        if (registeredHostId != null) {
            long now = System.currentTimeMillis();
            if (now - lastHeartbeatDbUpdate > 30_000) {
                HostInfo hostInfo = hostInfoService.getById(registeredHostId);
                if (hostInfo != null) {
                    hostInfo.setLastHeartbeat(LocalDateTime.now());
                    hostInfoService.updateById(hostInfo);
                }
                lastHeartbeatDbUpdate = now;
            }

            if (now - lastPerfAlertCheck > 30_000) {
                try {
                    alertService.checkPerformanceAlerts(registeredHostId, metric);
                } catch (Exception e) {
                    log.error("[#{}] Error checking performance alerts: {}", connectionId, e.getMessage());
                }
                lastPerfAlertCheck = now;
            }
        }
    }

    private void onProcessListReceived(List<HostProcess> processes) {
        if (registeredHostId == null) {
            log.warn("[#{}] No registered host, discarding process list", connectionId);
            return;
        }
        hostProcessService.batchInsert(registeredHostId, processes);
        log.debug("[#{}] Saved ProcessList: hostId={}, count={}", connectionId, registeredHostId, processes.size());
    }

    private void onProcessListFullReceived(SocketMessage msg, OutputStream outputStream) throws IOException {
        if (registeredHostId == null) {
            log.warn("[#{}] No registered host, discarding full process list", connectionId);
            return;
        }

        List<HostProcess> processes = MessageParser.parseProcessList(msg.getBody());

        processSnapshot.clear();
        LocalDateTime now = LocalDateTime.now();
        for (HostProcess hp : processes) {
            hp.setHostId(registeredHostId);
            hp.setCollectTime(now);
            processSnapshot.put(hp.getPid(), hp);
        }
        expectedProcessSeq = 2;

        hostProcessService.batchInsert(registeredHostId, processes);
        log.info("[#{}] Saved full ProcessList: hostId={}, count={}", connectionId, registeredHostId, processes.size());

        try {
            alertService.checkProcessAlerts(registeredHostId, processSnapshot);
        } catch (Exception e) {
            log.error("[#{}] Error checking process alerts: {}", connectionId, e.getMessage());
        }
    }

    private void onProcessListDeltaReceived(SocketMessage msg, OutputStream outputStream) throws IOException {
        if (registeredHostId == null) {
            log.warn("[#{}] No registered host, discarding delta process list", connectionId);
            return;
        }

        MessageParser.ProcessListDeltaDto delta = MessageParser.parseProcessListDelta(msg.getBody());

        if (delta.seq == null || delta.seq != expectedProcessSeq) {
            log.warn("[#{}] Process delta seq mismatch: expected={}, got={}, requesting resync",
                    connectionId, expectedProcessSeq, delta.seq);
            processSnapshot.clear();
            expectedProcessSeq = 1;
            sendCommand(MessageConstants.CMD_PROCESS_RESYNC);
            return;
        }

        if (delta.removed != null) {
            for (Integer pid : delta.removed) {
                processSnapshot.remove(pid);
            }
        }

        if (delta.added != null) {
            for (var dto : delta.added) {
                HostProcess hp = dtoToHostProcess(dto);
                processSnapshot.put(hp.getPid(), hp);
            }
        }

        if (delta.changed != null) {
            for (var dto : delta.changed) {
                HostProcess hp = dtoToHostProcess(dto);
                processSnapshot.put(hp.getPid(), hp);
            }
        }

        expectedProcessSeq++;

        LocalDateTime now = LocalDateTime.now();
        List<HostProcess> fullList = new java.util.ArrayList<>(processSnapshot.values());
        for (HostProcess hp : fullList) {
            hp.setHostId(registeredHostId);
            hp.setCollectTime(now);
        }
        hostProcessService.batchInsert(registeredHostId, fullList);

        log.debug("[#{}] Applied process delta: seq={}, added={}, removed={}, changed={}, total={}",
                connectionId, delta.seq,
                delta.added != null ? delta.added.size() : 0,
                delta.removed != null ? delta.removed.size() : 0,
                delta.changed != null ? delta.changed.size() : 0,
                fullList.size());

        try {
            alertService.checkProcessAlerts(registeredHostId, processSnapshot);
        } catch (Exception e) {
            log.error("[#{}] Error checking process alerts: {}", connectionId, e.getMessage());
        }
    }

    private HostProcess dtoToHostProcess(MessageParser.ProcessDto dto) {
        HostProcess hp = new HostProcess();
        hp.setPid(dto.pid != null ? dto.pid : 0);
        hp.setPpid(dto.ppid);
        hp.setName(dto.name);
        hp.setUser(dto.user);
        hp.setCpuUsage(dto.cpuUsage != null ? java.math.BigDecimal.valueOf(dto.cpuUsage) : java.math.BigDecimal.ZERO);
        hp.setMemoryUsage(dto.memoryUsage != null ? java.math.BigDecimal.valueOf(dto.memoryUsage) : java.math.BigDecimal.ZERO);
        hp.setMemoryUsed(dto.memoryUsed);
        hp.setStatus(dto.status);
        hp.setStartTime(dto.startTime);
        hp.setCommandLine(dto.commandLine);
        return hp;
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
