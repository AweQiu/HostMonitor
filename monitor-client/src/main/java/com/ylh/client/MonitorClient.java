package com.ylh.client;

import com.ylh.client.models.HostInfo;
import com.ylh.client.models.HostMetric;
import com.ylh.client.network.NetworkClient;
import com.ylh.client.utils.CollectionUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 监控客户端主入口
 * <p>
 * 启动后连接 Socket 服务器，采集当前主机的静态信息和实时性能指标，并通过网络发送给服务器。
 * </p>
 */
public class MonitorClient {

    public static void main(String[] args) {
        NetworkClient client = new NetworkClient();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            client.disconnect();
        }));

        try {
            client.connect();
            System.out.println("Connected to " + client.getHost() + ":" + client.getPort());

            HostInfo hostInfo = CollectionUtil.collectHostInfo();
            client.send(hostInfo.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("Host info sent.");

            while (true) {
                HostMetric metric = CollectionUtil.collectMetric();
                client.send(metric.toString().getBytes(StandardCharsets.UTF_8));

                Thread.sleep(1000);
            }
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            client.disconnect();
        }
    }
}
