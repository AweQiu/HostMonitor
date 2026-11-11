package com.ylh.client.network;

import com.ylh.client.common.CryptoService;
import com.ylh.client.common.MessageConstants;
import com.ylh.client.common.SocketMessage;
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
    private Consumer<SocketMessage> messageHandler;
    private final StringBuilder receiveBuffer = new StringBuilder();

    private CryptoService cryptoService;
    private boolean encryptionEnabled = false;

    public NetworkClient() {
        Properties props = ConfigUtil.loadProperties("application.properties");
        this.host = props.getProperty("socket.host", "127.0.0.1");
        this.port = Integer.parseInt(props.getProperty("socket.port", "10801"));
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
        String welcomeStr = receiveLine();
        System.out.println("Server: " + welcomeStr);

        try {
            cryptoService = new CryptoService();
            String clientPubKey = cryptoService.getPublicKeyBase64();
            SocketMessage authMsg = SocketMessage.authWithPubKey(agentKey, clientPubKey);
            sendRaw(authMsg.serialize().getBytes(StandardCharsets.UTF_8));

            String authResult = receiveLine();
            System.out.println("Auth: " + authResult);

            SocketMessage resp = SocketMessage.parse(authResult);
            if (resp == null || !resp.isOk()) {
                cryptoService = null;
                return false;
            }

            String serverPubKey = resp.getServerPubKey();
            if (serverPubKey != null && !serverPubKey.isEmpty()) {
                cryptoService.deriveAesKey(serverPubKey);
                encryptionEnabled = true;
                System.out.println("ECDH key exchange completed, encryption enabled");
            } else {
                System.out.println("Server did not provide public key, using plaintext");
                cryptoService = null;
            }

            return true;
        } catch (Exception e) {
            System.err.println("ECDH key exchange failed, falling back to plaintext: " + e.getMessage());
            cryptoService = null;
            encryptionEnabled = false;

            SocketMessage authMsg = SocketMessage.auth(agentKey);
            sendRaw(authMsg.serialize().getBytes(StandardCharsets.UTF_8));

            String authResult = receiveLine();
            System.out.println("Auth (plaintext): " + authResult);

            SocketMessage resp = SocketMessage.parse(authResult);
            return resp != null && resp.isOk();
        }
    }

    public void sendHostInfo() throws IOException {
        HostInfo hostInfo = CollectionUtil.collectHostInfo();
        SocketMessage msg = SocketMessage.hostInfo(hostInfo);
        sendMessage(msg);
    }

    public void sendMetric() throws IOException {
        HostMetric metric = CollectionUtil.collectMetric();
        SocketMessage msg = SocketMessage.hostMetric(metric);
        sendMessage(msg);
    }

    public void sendProcessMessage(SocketMessage msg) throws IOException {
        sendMessage(msg);
    }

    private void sendMessage(SocketMessage msg) throws IOException {
        if (encryptionEnabled && cryptoService != null) {
            String plainJson = msg.serialize().trim();
            String ciphertext = cryptoService.encrypt(plainJson);
            SocketMessage encMsg = SocketMessage.encrypted(ciphertext);
            sendRaw(encMsg.serialize().getBytes(StandardCharsets.UTF_8));
        } else {
            sendRaw(msg.serialize().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void sendRaw(byte[] data) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
        outputStream.write(data);
        outputStream.flush();
    }

    public void setMessageHandler(Consumer<SocketMessage> handler) {
        this.messageHandler = handler;
    }

    public void startListening() {
        if (listening) return;
        listening = true;

        listenerThread = new Thread(() -> {
            while (listening && isConnected()) {
                try {
                    String raw = receiveLine();
                    SocketMessage msg = SocketMessage.parse(raw);
                    if (msg == null) {
                        continue;
                    }

                    if (MessageConstants.TYPE_ENCRYPTED.equals(msg.getType())) {
                        if (!encryptionEnabled || cryptoService == null) {
                            System.err.println("Received encrypted message but encryption not enabled");
                            continue;
                        }
                        String ciphertext = msg.getCiphertext();
                        if (ciphertext == null) continue;
                        try {
                            String decrypted = cryptoService.decrypt(ciphertext);
                            msg = SocketMessage.parse(decrypted);
                            if (msg == null) continue;
                        } catch (Exception e) {
                            System.err.println("Failed to decrypt message: " + e.getMessage());
                            continue;
                        }
                    }

                    if (messageHandler != null) {
                        messageHandler.accept(msg);
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
        cryptoService = null;
        encryptionEnabled = false;
        receiveBuffer.setLength(0);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void send(byte[] data) throws IOException {
        sendRaw(data);
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

    /**
     * 按行读取一条完整消息（以 \n 结尾）
     * <p>读取到的多余数据会保留在缓冲区中，供后续调用继续消费，避免 TCP 粘包导致的解析错位。</p>
     */
    public String receiveLine() throws IOException {
        while (true) {
            int nlIdx = receiveBuffer.indexOf("\n");
            if (nlIdx != -1) {
                String line = receiveBuffer.substring(0, nlIdx).trim();
                receiveBuffer.delete(0, nlIdx + 1);
                if (!line.isEmpty()) {
                    return line;
                }
                continue;
            }
            if (!isConnected()) {
                throw new IOException("Not connected to server");
            }
            byte[] readBuf = new byte[8192];
            int bytesRead = inputStream.read(readBuf);
            if (bytesRead == -1) {
                throw new IOException("Connection closed by server");
            }
            receiveBuffer.append(new String(readBuf, 0, bytesRead, StandardCharsets.UTF_8));
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public CryptoService getCryptoService() {
        return cryptoService;
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
}
