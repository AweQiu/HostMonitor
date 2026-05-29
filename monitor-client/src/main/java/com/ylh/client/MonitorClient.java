package com.ylh.client;

import com.ylh.client.common.MessageConstants;
import com.ylh.client.common.SocketMessage;
import com.ylh.client.core.MetricReporter;
import com.ylh.client.core.ProcessReporter;
import com.ylh.client.core.ScreenStreamer;
import com.ylh.client.network.NetworkClient;
import com.ylh.client.network.UdpSender;
import com.ylh.client.utils.ConfigUtil;

import java.io.IOException;
import java.util.Properties;

public class MonitorClient {

    private NetworkClient client;
    private MetricReporter metricReporter;
    private ProcessReporter processReporter;
    private ScreenStreamer screenStreamer;
    private UdpSender udpSender;
    private boolean screenEnabled;
    private int screenFps;
    private float jpegQuality;

    public void start() {
        Properties props = ConfigUtil.loadProperties("application.properties");
        String agentKey = props.getProperty("agent.key", "");

        if (agentKey.isEmpty()) {
            System.err.println("Agent key is not configured. Please set 'agent.key' in application.properties");
            return;
        }

        screenEnabled = Boolean.parseBoolean(props.getProperty("screen.enabled", "true"));
        long metricInterval = Long.parseLong(props.getProperty("metric.interval.ms", "5000"));
        long processInterval = Long.parseLong(props.getProperty("process.interval.ms", "15000"));
        screenFps = Integer.parseInt(props.getProperty("screen.fps", "5"));
        jpegQuality = Float.parseFloat(props.getProperty("screen.jpeg.quality", "0.5"));

        client = new NetworkClient();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        try {
            client.connect();
            System.out.println("Connected to " + client.getHost() + ":" + client.getPort());

            if (!client.authenticate(agentKey)) {
                System.err.println("Authentication failed");
                return;
            }

            client.sendHostInfo();

            metricReporter = new MetricReporter(client, metricInterval);
            metricReporter.start();

            processReporter = new ProcessReporter(client, processInterval);
            processReporter.start();

            if (screenEnabled) {
                udpSender = new UdpSender();

                client.setMessageHandler(msg -> {
                    if (MessageConstants.TYPE_CMD.equals(msg.getType())) {
                        String command = msg.getCommand();
                        System.out.println("Received command: " + command);
                        if (MessageConstants.CMD_SCREEN_START.equals(command)) {
                            startScreenStreaming();
                        } else if (MessageConstants.CMD_SCREEN_STOP.equals(command)) {
                            stopScreenStreaming();
                        } else if (MessageConstants.CMD_PROCESS_RESYNC.equals(command)) {
                            if (processReporter != null) {
                                processReporter.requestResync();
                            }
                        }
                    }
                });

                client.startListening();
                System.out.println("Screen streaming is on-demand, waiting for " + MessageConstants.CMD_SCREEN_START + " command...");
            }

            Thread.currentThread().join();
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop();
        }
    }

    private synchronized void startScreenStreaming() {
        if (screenStreamer != null && screenStreamer.isRunning()) {
            return;
        }
        screenStreamer = new ScreenStreamer(udpSender, screenFps, jpegQuality);
        screenStreamer.start();
    }

    private synchronized void stopScreenStreaming() {
        if (screenStreamer != null) {
            screenStreamer.stop();
            screenStreamer = null;
        }
    }

    public void stop() {
        stopScreenStreaming();
        if (udpSender != null) {
            udpSender.disconnect();
            udpSender = null;
        }
        if (metricReporter != null) {
            metricReporter.stop();
            metricReporter = null;
        }
        if (processReporter != null) {
            processReporter.stop();
            processReporter = null;
        }
        if (client != null) {
            client.disconnect();
            client = null;
        }
        System.out.println("Monitor client stopped");
    }

    public static void main(String[] args) {
        new MonitorClient().start();
    }
}
