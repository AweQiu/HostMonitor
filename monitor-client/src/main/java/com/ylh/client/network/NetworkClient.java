package com.ylh.client.network;

import com.ylh.client.utils.ConfigUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Properties;

/**
 * Socket 网络客户端
 * <p>
 * 负责与远程 Socket 服务器建立 TCP 连接，提供数据的发送与接收能力。
 * 服务器地址和端口支持两种配置方式：
 * <ul>
 *     <li>通过无参构造函数自动从 {@code application.properties} 配置文件加载</li>
 *     <li>通过带参构造函数手动指定 host 和 port</li>
 * </ul>
 * </p>
 *
 * <p><b>典型用法：</b></p>
 * <pre>
 *     NetworkClient client = new NetworkClient();  // 从配置文件读取
 *     client.connect();
 *     client.send(data);
 *     byte[] response = client.receive(4096);
 *     client.disconnect();
 * </pre>
 */
public class NetworkClient {

    /** Socket 服务器地址 */
    private String host;

    /** Socket 服务器端口 */
    private int port;

    /** 底层 Socket 连接对象 */
    private Socket socket;

    /** 输出流，用于向服务器发送数据 */
    private OutputStream outputStream;

    /** 输入流，用于从服务器接收数据 */
    private InputStream inputStream;

    /**
     * 无参构造函数
     * <p>
     * 通过 {@link ConfigUtil} 从 classpath 下的 {@code application.properties} 文件中读取
     * {@code socket.host} 和 {@code socket.port} 配置项，
     * 未配置时使用默认值 127.0.0.1:12345。
     * </p>
     *
     * @throws RuntimeException 配置文件不存在或读取失败时抛出
     */
    public NetworkClient() {
        Properties props = ConfigUtil.loadProperties("application.properties");
        this.host = props.getProperty("socket.host", "127.0.0.1");
        this.port = Integer.parseInt(props.getProperty("socket.port", "12345"));
    }

    /**
     * 手动指定服务器地址和端口
     *
     * @param host 服务器 IP 地址或域名
     * @param port 服务器端口号
     */
    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 建立 Socket 连接
     * <p>
     * 连接到构造时指定的服务器地址和端口，并获取输入/输出流。
     * 如果连接已存在且未关闭，则直接返回不重复连接。
     * </p>
     *
     * @throws IOException 连接失败时抛出
     */
    public void connect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            return;
        }
        socket = new Socket(host, port);
        outputStream = socket.getOutputStream();
        inputStream = socket.getInputStream();
    }

    /**
     * 断开 Socket 连接
     * <p>
     * 依次关闭输入流、输出流和 Socket，并将所有引用置为 null。
     * 关闭过程中的 IO 异常会被静默忽略。
     * </p>
     */
    public void disconnect() {
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

    /**
     * 检查连接是否处于可用状态
     *
     * @return {@code true} 表示连接可用，{@code false} 表示未连接或已断开
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * 向服务器发送字节数据
     *
     * @param data 要发送的字节数组
     * @throws IOException 未连接或发送失败时抛出
     */
    public void send(byte[] data) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
        outputStream.write(data);
        outputStream.flush();
    }

    /**
     * 从服务器接收字节数据
     * <p>
     * 最多读取 {@code maxBytes} 个字节。如果服务器关闭了连接
     * （返回 -1），则抛出 IOException。
     * </p>
     *
     * @param maxBytes 单次读取的最大字节数
     * @return 实际接收到的字节数组（长度可能小于 maxBytes）
     * @throws IOException 未连接、接收失败或服务器关闭连接时抛出
     */
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
     * 获取服务器地址
     *
     * @return 服务器 IP 地址或域名
     */
    public String getHost() {
        return host;
    }

    /**
     * 获取服务器端口
     *
     * @return 端口号
     */
    public int getPort() {
        return port;
    }
}
