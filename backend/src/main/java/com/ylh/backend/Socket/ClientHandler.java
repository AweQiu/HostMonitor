package com.ylh.backend.Socket;

import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.HostMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private static final int BUFFER_SIZE = 8192;

    private final Socket clientSocket;
    private final long connectionId;
    private final StringBuilder messageBuffer = new StringBuilder();
    private final AtomicLong lastActiveTime = new AtomicLong(System.currentTimeMillis());

    public ClientHandler(Socket clientSocket, long connectionId) {
        this.clientSocket = clientSocket;
        this.connectionId = connectionId;
    }

    @Override
    public void run() {
        String clientAddress = clientSocket.getRemoteSocketAddress().toString();
        log.info("[#{}] Client connected: {}", connectionId, clientAddress);

        try (InputStream inputStream = clientSocket.getInputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }
                    lastActiveTime.set(System.currentTimeMillis());
                    String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    messageBuffer.append(chunk);
                    processBuffer();
                } catch (SocketTimeoutException e) {
                    continue;
                }
            }
        } catch (IOException e) {
            log.info("[#{}] Client disconnected: {}", connectionId, e.getMessage());
        } finally {
            closeQuietly();
            log.info("[#{}] Handler finished for: {}", connectionId, clientAddress);
        }
    }

    private void processBuffer() {
        while (true) {
            String data = messageBuffer.toString();
            int startIndex = findMessageStart(data);
            if (startIndex == -1) {
                messageBuffer.setLength(0);
                return;
            }

            if (startIndex > 0) {
                messageBuffer.delete(0, startIndex);
                data = messageBuffer.toString();
            }

            int nextStart = findNextMessageStart(data);
            if (nextStart == -1) {
                return;
            }

            String message = messageBuffer.substring(0, nextStart);
            messageBuffer.delete(0, nextStart);

            handleMessage(message.trim());
        }
    }

    private int findMessageStart(String data) {
        int hostInfoIdx = data.indexOf("HostInfo{");
        int hostMetricIdx = data.indexOf("HostMetric{");
        if (hostInfoIdx == -1 && hostMetricIdx == -1) return -1;
        if (hostInfoIdx == -1) return hostMetricIdx;
        if (hostMetricIdx == -1) return hostInfoIdx;
        return Math.min(hostInfoIdx, hostMetricIdx);
    }

    private int findNextMessageStart(String data) {
        int searchFrom = 9;
        int hostInfoIdx = data.indexOf("HostInfo{", searchFrom);
        int hostMetricIdx = data.indexOf("HostMetric{", searchFrom);
        if (hostInfoIdx == -1 && hostMetricIdx == -1) return -1;
        if (hostInfoIdx == -1) return hostMetricIdx;
        if (hostMetricIdx == -1) return hostInfoIdx;
        return Math.min(hostInfoIdx, hostMetricIdx);
    }

    private void handleMessage(String message) {
        try {
            if (MessageParser.isHostInfoMessage(message)) {
                HostInfo host = MessageParser.parseHostInfo(message);
                onHostInfoReceived(host);
            } else if (MessageParser.isHostMetricMessage(message)) {
                HostMetric metric = MessageParser.parseHostMetric(message);
                onHostMetricReceived(metric);
            } else {
                log.warn("[#{}] Unknown message type: {}", connectionId, message);
            }
        } catch (Exception e) {
            log.error("[#{}] Failed to parse message: {}", connectionId, message, e);
        }
    }

    protected void onHostInfoReceived(HostInfo host) {
        log.info("[#{}] Received HostInfo: id={}, hostname={}, ip={}, os={}/{}",
                connectionId, host.getId(), host.getHostname(),
                host.getIpAddress(), host.getOsName(), host.getOsVersion());
    }

    protected void onHostMetricReceived(HostMetric metric) {
        log.info("[#{}] Received HostMetric: id={}, hostId={}, cpu={}%, mem={}%, disk={}%",
                connectionId, metric.getId(), metric.getHostId(),
                metric.getCpuUsage(), metric.getMemoryUsage(), metric.getDiskUsage());
    }

    public long getLastActiveTime() {
        return lastActiveTime.get();
    }

    public long getConnectionId() {
        return connectionId;
    }

    public void closeQuietly() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public String getClientAddress() {
        if (clientSocket == null || clientSocket.isClosed()) {
            return "disconnected";
        }
        return clientSocket.getRemoteSocketAddress().toString();
    }
}
