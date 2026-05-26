package com.ylh.client;

import com.ylh.client.models.HostInfo;
import com.ylh.client.models.HostMetric;
import com.ylh.client.network.NetworkClient;
import com.ylh.client.utils.CollectionUtil;
import com.ylh.client.utils.ConfigUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class MonitorClient {

    public static void main(String[] args) {
        Properties props = ConfigUtil.loadProperties("application.properties");
        String agentKey = props.getProperty("agent.key", "");

        if (agentKey.isEmpty()) {
            System.err.println("Agent key is not configured. Please set 'agent.key' in application.properties");
            return;
        }

        NetworkClient client = new NetworkClient();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            client.disconnect();
        }));

        try {
            client.connect();
            System.out.println("Connected to " + client.getHost() + ":" + client.getPort());

            Thread.sleep(500);
            byte[] welcome = client.receive(4096);
            System.out.println("Server: " + new String(welcome, StandardCharsets.UTF_8).trim());

            client.send(("AUTH:" + agentKey + "\n").getBytes(StandardCharsets.UTF_8));
            Thread.sleep(500);
            byte[] authResp = client.receive(4096);
            String authResult = new String(authResp, StandardCharsets.UTF_8).trim();
            System.out.println("Auth: " + authResult);

            if (!authResult.contains("OK")) {
                System.err.println("Authentication failed, exiting.");
                client.disconnect();
                return;
            }

            HostInfo hostInfo = CollectionUtil.collectHostInfo();
            client.send((hostInfo.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            Thread.sleep(500);
            byte[] hostResp = client.receive(4096);
            System.out.println("HostInfo: " + new String(hostResp, StandardCharsets.UTF_8).trim());

            while (true) {
                HostMetric metric = CollectionUtil.collectMetric();
                client.send((metric.toString() + "\n").getBytes(StandardCharsets.UTF_8));
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
