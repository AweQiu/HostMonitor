package com.ylh.backend.Socket;

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

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final AtomicLong connectionIdGenerator = new AtomicLong(0);
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
    private final ConcurrentHashMap<Long, ClientHandler> clients = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
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
                    ClientHandler handler = new ClientHandler(clientSocket, connectionId);
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

    @PreDestroy
    public void stop() {
        running = false;
        log.info("Shutting down socket server, {} clients connected...", clients.size());

        for (ClientHandler handler : clients.values()) {
            handler.closeQuietly();
        }
        clients.clear();

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
}
