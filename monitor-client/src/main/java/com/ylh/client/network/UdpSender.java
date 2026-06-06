package com.ylh.client.network;

import com.ylh.client.utils.ConfigUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class UdpSender {

    private static final int MAGIC = 0x5343524E;
    private static final int MAX_UDP_SIZE = 60000;

    private final String host;
    private final int port;
    private final String agentKey;
    private DatagramSocket socket;
    private InetAddress serverAddress;

    public UdpSender() {
        Properties props = ConfigUtil.loadProperties("application.properties");
        this.host = props.getProperty("screen.udp.host", props.getProperty("socket.host", "127.0.0.1"));
        this.port = Integer.parseInt(props.getProperty("screen.udp.port", "10802"));
        this.agentKey = props.getProperty("agent.key", "");
    }

    public UdpSender(String host, int port, String agentKey) {
        this.host = host;
        this.port = port;
        this.agentKey = agentKey;
    }

    public void connect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            return;
        }
        socket = new DatagramSocket();
        serverAddress = InetAddress.getByName(host);
        System.out.println("UDP sender ready: " + host + ":" + port);
    }

    // 将 JPEG 帧封装为 UDP 数据包发送：小包（≤60KB）单包发送，大包自动分块发送（每块额外带 totalChunks + chunkIndex 头）
    // 协议格式：[MAGIC 4字节][keyLength 4字节][agentKey UTF-8][可选: totalChunks 4字节 + chunkIndex 4字节][JPEG数据]
    public void sendFrame(byte[] jpegData) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("UDP socket not connected");
        }
        if (jpegData == null || jpegData.length == 0) {
            return;
        }

        byte[] keyBytes = agentKey.getBytes(StandardCharsets.UTF_8);
        int headerSize = 4 + 4 + keyBytes.length;
        int totalSize = headerSize + jpegData.length;

        if (totalSize <= MAX_UDP_SIZE) {
            byte[] packet = new byte[totalSize];
            packet[0] = (byte) (MAGIC >>> 24);
            packet[1] = (byte) (MAGIC >>> 16);
            packet[2] = (byte) (MAGIC >>> 8);
            packet[3] = (byte) MAGIC;
            packet[4] = (byte) (keyBytes.length >>> 24);
            packet[5] = (byte) (keyBytes.length >>> 16);
            packet[6] = (byte) (keyBytes.length >>> 8);
            packet[7] = (byte) keyBytes.length;
            System.arraycopy(keyBytes, 0, packet, 8, keyBytes.length);
            System.arraycopy(jpegData, 0, packet, headerSize, jpegData.length);

            DatagramPacket datagram = new DatagramPacket(packet, packet.length, serverAddress, port);
            socket.send(datagram);
        } else {
            int chunkPayloadSize = MAX_UDP_SIZE - headerSize - 8;
            int totalChunks = (jpegData.length + chunkPayloadSize - 1) / chunkPayloadSize;

            for (int i = 0; i < totalChunks; i++) {
                int offset = i * chunkPayloadSize;
                int length = Math.min(chunkPayloadSize, jpegData.length - offset);
                int chunkHeaderSize = headerSize + 8;
                byte[] chunkPacket = new byte[chunkHeaderSize + length];

                chunkPacket[0] = (byte) (MAGIC >>> 24);
                chunkPacket[1] = (byte) (MAGIC >>> 16);
                chunkPacket[2] = (byte) (MAGIC >>> 8);
                chunkPacket[3] = (byte) MAGIC;
                chunkPacket[4] = (byte) (keyBytes.length >>> 24);
                chunkPacket[5] = (byte) (keyBytes.length >>> 16);
                chunkPacket[6] = (byte) (keyBytes.length >>> 8);
                chunkPacket[7] = (byte) keyBytes.length;
                System.arraycopy(keyBytes, 0, chunkPacket, 8, keyBytes.length);

                int pos = headerSize;
                chunkPacket[pos++] = (byte) (totalChunks >>> 24);
                chunkPacket[pos++] = (byte) (totalChunks >>> 16);
                chunkPacket[pos++] = (byte) (totalChunks >>> 8);
                chunkPacket[pos++] = (byte) totalChunks;
                chunkPacket[pos++] = (byte) (i >>> 24);
                chunkPacket[pos++] = (byte) (i >>> 16);
                chunkPacket[pos++] = (byte) (i >>> 8);
                chunkPacket[pos++] = (byte) i;

                System.arraycopy(jpegData, offset, chunkPacket, pos, length);

                DatagramPacket datagram = new DatagramPacket(chunkPacket, chunkPacket.length, serverAddress, port);
                socket.send(datagram);
            }
        }
    }

    public void disconnect() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
