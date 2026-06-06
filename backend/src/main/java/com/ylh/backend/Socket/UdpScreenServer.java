package com.ylh.backend.Socket;

import com.ylh.backend.Entity.User;
import com.ylh.backend.Service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UdpScreenServer {

    private static final Logger log = LoggerFactory.getLogger(UdpScreenServer.class);
    private static final int MAGIC = 0x5343524E;
    private static final int MAX_PACKET_SIZE = 65507;
    private static final long CHUNK_TIMEOUT_MS = 2000;

    @Value("${screen.udp.port:10802}")
    private int port;

    private final UserService userService;
    private final ScreenStreamManager streamManager;

    private DatagramSocket socket;
    private volatile boolean running = false;

    private final Map<Long, ChunkBuffer> chunkBuffers = new ConcurrentHashMap<>();
    private final Map<String, Long> agentKeyCache = new ConcurrentHashMap<>();

    public UdpScreenServer(UserService userService, ScreenStreamManager streamManager) {
        this.userService = userService;
        this.streamManager = streamManager;
    }

    // 启动两个守护线程：UDP 监听线程（接收客户端帧数据）和过期缓冲清理线程（清理超时未完成的分包）
    @PostConstruct
    public void start() {
        Thread udpThread = new Thread(this::listen, "udp-screen-server");
        udpThread.setDaemon(true);
        udpThread.start();

        Thread cleanupThread = new Thread(this::cleanupStaleBuffers, "chunk-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    // UDP 监听循环：持续接收 UDP 数据包，调用 processPacket 解析处理
    private void listen() {
        try {
            socket = new DatagramSocket(port);
            running = true;
            log.info("UDP screen server started on port {}", port);

            byte[] buffer = new byte[MAX_PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (running) {
                try {
                    socket.receive(packet);
                    processPacket(packet.getData(), packet.getLength());
                } catch (Exception e) {
                    if (running) {
                        log.error("Error receiving UDP packet", e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to start UDP screen server on port {}", port, e);
        }
    }

    // 解析 UDP 数据包：验证 MAGIC 头 → 提取 agentKey 查找 userId → 检查是否有订阅者 → 单包帧直接转发，分包帧用 ChunkBuffer 组装后转发
    private void processPacket(byte[] data, int length) {
        if (length < 8) {
            return;
        }

        int magic = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        if (magic != MAGIC) {
            return;
        }

        int keyLength = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) |
                ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);

        if (keyLength <= 0 || keyLength > 128) {
            return;
        }

        int headerSize = 8 + keyLength;
        if (length < headerSize) {
            return;
        }

        String agentKey = new String(data, 8, keyLength, StandardCharsets.UTF_8);

        Long userId = agentKeyCache.get(agentKey);
        if (userId == null) {
            User user = userService.getByAgentKey(agentKey);
            if (user == null) {
                return;
            }
            userId = user.getId();
            agentKeyCache.put(agentKey, userId);
        }

        if (!streamManager.hasSubscribers(userId)) {
            return;
        }

        if (length > headerSize + 8) {
            int totalChunks = ((data[headerSize] & 0xFF) << 24) | ((data[headerSize + 1] & 0xFF) << 16) |
                    ((data[headerSize + 2] & 0xFF) << 8) | (data[headerSize + 3] & 0xFF);
            int chunkIndex = ((data[headerSize + 4] & 0xFF) << 24) | ((data[headerSize + 5] & 0xFF) << 16) |
                    ((data[headerSize + 6] & 0xFF) << 8) | (data[headerSize + 7] & 0xFF);

            if (totalChunks <= 1) {
                int chunkDataStart = headerSize + 8;
                byte[] jpegData = Arrays.copyOfRange(data, chunkDataStart, length);
                streamManager.updateFrame(userId, jpegData);
                return;
            }

            int chunkDataStart = headerSize + 8;
            byte[] chunkData = Arrays.copyOfRange(data, chunkDataStart, length);

            ChunkBuffer chunkBuffer = chunkBuffers.computeIfAbsent(userId, k -> new ChunkBuffer());
            boolean complete = chunkBuffer.addChunk(totalChunks, chunkIndex, chunkData);
            if (complete) {
                byte[] fullFrame = chunkBuffer.assemble();
                chunkBuffers.remove(userId);
                if (fullFrame != null && fullFrame.length > 0) {
                    streamManager.updateFrame(userId, fullFrame);
                }
            }
            return;
        }

        byte[] jpegData = Arrays.copyOfRange(data, headerSize, length);
        streamManager.updateFrame(userId, jpegData);
    }

    // 每 3 秒清理超过 2 秒未完成的 ChunkBuffer，防止内存泄漏
    private void cleanupStaleBuffers() {
        while (running) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            long now = System.currentTimeMillis();
            chunkBuffers.entrySet().removeIf(entry -> now - entry.getValue().createdAt > CHUNK_TIMEOUT_MS);
        }
    }

    public void invalidateAgentKeyCache(String agentKey) {
        agentKeyCache.remove(agentKey);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        log.info("UDP screen server stopped");
    }

    // 分包缓冲区：按 chunkIndex 存储分块数据，所有块到齐后按序拼接为完整 JPEG 帧
    private static class ChunkBuffer {
        final long createdAt = System.currentTimeMillis();
        int totalChunks;
        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        volatile boolean initialized = false;

        // 添加一个分块，返回 true 表示所有分块已到齐
        boolean addChunk(int totalChunks, int chunkIndex, byte[] data) {
            if (!initialized) {
                this.totalChunks = totalChunks;
                this.initialized = true;
            }
            chunks.put(chunkIndex, data);
            return chunks.size() == this.totalChunks;
        }

        // 将所有分块按序拼接为完整字节数组
        byte[] assemble() {
            int totalSize = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = chunks.get(i);
                if (chunk == null) {
                    return null;
                }
                totalSize += chunk.length;
            }
            byte[] result = new byte[totalSize];
            int offset = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = chunks.get(i);
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            return result;
        }
    }
}
