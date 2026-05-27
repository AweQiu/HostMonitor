package com.ylh.client.network;

import com.ylh.client.models.HostInfo;
import com.ylh.client.models.HostMetric;
import com.ylh.client.utils.CollectionUtil;
import com.ylh.client.utils.ConfigUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Consumer;

public class NetworkClient {

    private String host;
    private int port;
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread listenerThread;
    private volatile boolean listening = false;
    private Consumer<String> commandHandler;

    public NetworkClient() {
        Properties props = ConfigUtil.loadProperties("application.properties");
        this.host = props.getProperty("socket.host", "127.0.0.1");
        this.port = Integer.parseInt(props.getProperty("socket.port", "12345"));
    }

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            return;
        }
        socket = new Socket(host, port);
        outputStream = socket.getOutputStream();
        inputStream = socket.getInputStream();
    }

    public boolean authenticate(String agentKey) throws IOException {
        byte[] welcome = receive(4096);
        System.out.println("Server: " + new String(welcome, StandardCharsets.UTF_8).trim());

        send(("AUTH:" + agentKey + "\n").getBytes(StandardCharsets.UTF_8));

        byte[] authResp = receive(4096);
        String authResult = new String(authResp, StandardCharsets.UTF_8).trim();
        System.out.println("Auth: " + authResult);

        return authResult.contains("OK");
    }

    public void sendHostInfo() throws IOException {
        HostInfo hostInfo = CollectionUtil.collectHostInfo();
        send((hostInfo.toString() + "\n").getBytes(StandardCharsets.UTF_8));
    }

    public void sendMetric() throws IOException {
        HostMetric metric = CollectionUtil.collectMetric();
        send((metric.toString() + "\n").getBytes(StandardCharsets.UTF_8));
    }

    public void setCommandHandler(Consumer<String> handler) {
        this.commandHandler = handler;
    }

    public void startListening() {
        if (listening) return;
        listening = true;

        listenerThread = new Thread(() -> {
            StringBuilder buffer = new StringBuilder();
            byte[] readBuf = new byte[4096];

            while (listening && isConnected()) {
                try {
                    int bytesRead = inputStream.read(readBuf);
                    if (bytesRead == -1) {
                        break;
                    }

                    String chunk = new String(readBuf, 0, bytesRead, StandardCharsets.UTF_8);
                    buffer.append(chunk);

                    int nlIdx;
                    while ((nlIdx = buffer.indexOf("\n")) != -1) {
                        String line = buffer.substring(0, nlIdx).trim();
                        buffer.delete(0, nlIdx + 1);

                        if (isCommand(line) && commandHandler != null) {
                            commandHandler.accept(line);
                        }
                    }
                } catch (IOException e) {
                    if (listening) {
                        System.err.println("Command listener error: " + e.getMessage());
                    }
                    break;
                }
            }
            listening = false;
        }, "command-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        System.out.println("Command listener started");
    }

    public void stopListening() {
        listening = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }

    private boolean isCommand(String line) {
        return line.startsWith("SCREEN_");
    }

    public void disconnect() {
        stopListening();
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException ignored) {
        }
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        outputStream = null;
        inputStream = null;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void send(byte[] data) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
        outputStream.write(data);
        outputStream.flush();
    }

    public byte[] receive(int maxBytes) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
        byte[] buffer = new byte[maxBytes];
        int bytesRead = inputStream.read(buffer);
        if (bytesRead == -1) {
            throw new IOException("Connection closed by server");
        }
        byte[] result = new byte[bytesRead];
        System.arraycopy(buffer, 0, result, 0, bytesRead);
        return result;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
