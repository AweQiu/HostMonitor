package com.ylh.backend.Socket;

import com.ylh.backend.Mapper.HostMetricMapper;
import com.ylh.backend.Service.HostInfoService;
import com.ylh.backend.Service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SocketServer {

    private static final Logger log = LoggerFactory.getLogger(SocketServer.class);
    private static final int MAX_THREADS = 50;
    private static final long HEARTBEAT_CHECK_INTERVAL_MS = 30_000;
    private static final long HEARTBEAT_TIMEOUT_MS = 60_000;

    @Value("${socket.port:12345}")
    private int port;

    private final UserService userService;
    private final HostInfoService hostInfoService;
    private final HostMetricMapper hostMetricMapper;
    private final ScreenStreamManager screenStreamManager;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final AtomicLong connectionIdGenerator = new AtomicLong(0);
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
    private final ConcurrentHashMap<Long, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> userConnectionMap = new ConcurrentHashMap<>();

    public SocketServer(UserService userService, HostInfoService hostInfoService,
                        HostMetricMapper hostMetricMapper, ScreenStreamManager screenStreamManager) {
        this.userService = userService;
        this.hostInfoService = hostInfoService;
        this.hostMetricMapper = hostMetricMapper;
        this.screenStreamManager = screenStreamManager;
    }

    @PostConstruct
    public void start() {
        screenStreamManager.setSubscriptionListener(new ScreenStreamManager.ScreenSubscriptionListener() {
            @Override
            public void onFirstSubscriber(Long userId) {
                sendCommandToUser(userId, "SCREEN_START");
            }

            @Override
            public void onLastSubscriberLeft(Long userId) {
                sendCommandToUser(userId, "SCREEN_STOP");
            }
        });

        Thread serverThread = new Thread(this::listen, "socket-server");
        serverThread.setDaemon(true);
        serverThread.start();

        Thread heartbeatThread = new Thread(this::checkHeartbeats, "heartbeat-checker");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void listen() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            log.info("Socket server started on port {}", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(5000);
                    long connectionId = connectionIdGenerator.incrementAndGet();
                    ClientHandler handler = new ClientHandler(
                            clientSocket, connectionId, userService, hostInfoService, hostMetricMapper, this
                    );
                    clients.put(connectionId, handler);
                    log.info("New connection #{} from {}, total clients: {}",
                            connectionId, clientSocket.getRemoteSocketAddress(), clients.size());

                    executor.submit(() -> {
                        handler.run();
                        clients.remove(connectionId);
                        log.info("Connection #{} removed, total clients: {}",
                                connectionId, clients.size());
                    });
                } catch (IOException e) {
                    if (running) {
                        log.error("Error accepting client connection", e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to start socket server on port {}", port, e);
        }
    }

    private void checkHeartbeats() {
        while (running) {
            try {
                Thread.sleep(HEARTBEAT_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            long now = System.currentTimeMillis();
            for (var entry : clients.entrySet()) {
                ClientHandler handler = entry.getValue();
                long lastActive = handler.getLastActiveTime();
                if (now - lastActive > HEARTBEAT_TIMEOUT_MS) {
                    log.warn("Client #{} heartbeat timeout (last active {}ms ago), closing connection",
                            entry.getKey(), now - lastActive);
                    handler.closeQuietly();
                }
            }
        }
    }

    public void registerUserConnection(Long userId, Long connectionId) {
        if (userId == null) return;
        userConnectionMap.put(userId, connectionId);
        log.info("User {} bound to connection #{}", userId, connectionId);
    }

    public void kickExistingConnection(Long userId, Long newConnectionId) {
        if (userId == null) return;
        Long existingConnId = userConnectionMap.get(userId);
        if (existingConnId != null && !existingConnId.equals(newConnectionId)) {
            ClientHandler existingHandler = clients.get(existingConnId);
            if (existingHandler != null) {
                log.info("Kicking existing connection #{} for user {} (new connection #{})",
                        existingConnId, userId, newConnectionId);
                existingHandler.closeQuietly();
            }
            userConnectionMap.remove(userId, existingConnId);
        }
    }

    public void removeUserConnection(Long userId, Long connectionId) {
        if (userId == null) return;
        userConnectionMap.remove(userId, connectionId);
        log.info("User {} connection #{} removed from mapping", userId, connectionId);
    }

    @PreDestroy
    public void stop() {
        running = false;
        log.info("Shutting down socket server, {} clients connected...", clients.size());

        for (ClientHandler handler : clients.values()) {
            handler.closeQuietly();
        }
        clients.clear();
        userConnectionMap.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error closing server socket", e);
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Socket server stopped");
    }

    public int getConnectedClientCount() {
        return clients.size();
    }

    public boolean isRunning() {
        return running;
    }

    public void sendCommandToUser(Long userId, String command) {
        if (userId == null || command == null) return;
        Long connId = userConnectionMap.get(userId);
        if (connId == null) {
            log.warn("No TCP connection found for user {}, cannot send command: {}", userId, command);
            return;
        }
        ClientHandler handler = clients.get(connId);
        if (handler == null) {
            log.warn("No ClientHandler found for connection #{}, cannot send command: {}", connId, command);
            return;
        }
        handler.sendCommand(command);
        log.info("Sent command '{}' to user {} via connection #{}", command, userId, connId);
    }
}
