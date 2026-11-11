package com.ylh.client;

import com.ylh.client.common.MessageConstants;
import com.ylh.client.common.RuntimeConfig;
import com.ylh.client.common.SocketMessage;
import com.ylh.client.core.MetricReporter;
import com.ylh.client.core.ProcessReporter;
import com.ylh.client.core.ScreenStreamer;
import com.ylh.client.network.NetworkClient;
import com.ylh.client.network.UdpSender;
import com.ylh.client.utils.ConfigUtil;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class MonitorClient {

    private NetworkClient client;
    private MetricReporter metricReporter;
    private ProcessReporter processReporter;
    private ScreenStreamer screenStreamer;
    private UdpSender udpSender;
    private boolean screenEnabled;

    public void start() {
        Properties props = ConfigUtil.loadProperties("application.yml");
        String agentKey = props.getProperty("agent.key", "");

        if (agentKey.isEmpty()) {
            System.err.println("Agent key is not configured. Please set 'agent.key' in application.yml");
            return;
        }

        RuntimeConfig runtimeConfig = RuntimeConfig.getInstance();
        runtimeConfig.initFromProperties(props);

        screenEnabled = Boolean.parseBoolean(props.getProperty("screen.enabled", "true"));

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

            metricReporter = new MetricReporter(client);
            metricReporter.start();

            processReporter = new ProcessReporter(client);
            processReporter.start();

            if (screenEnabled) {
                udpSender = new UdpSender();
                if (client.isEncryptionEnabled()) {
                    udpSender.setCryptoService(client.getCryptoService());
                    System.out.println("UDP encryption enabled (shared CryptoService with TCP)");
                }

                client.setMessageHandler(msg -> {
                    if (MessageConstants.TYPE_CONFIG.equals(msg.getType())) {
                        Map<String, String> config = msg.getConfigMap();
                        if (config != null) {
                            runtimeConfig.applyServerConfig(config);
                        }
                    } else if (MessageConstants.TYPE_CMD.equals(msg.getType())) {
                        String command = msg.getCommand();
                        System.out.println("Received command: " + command);
                        if (MessageConstants.CMD_SCREEN_START.equals(command)) {
                            if (screenEnabled) {
                                startScreenStreaming();
                            }
                        } else if (MessageConstants.CMD_SCREEN_STOP.equals(command)) {
                            if (screenEnabled) {
                                stopScreenStreaming();
                            }
                        } else if (MessageConstants.CMD_PROCESS_RESYNC.equals(command)) {
                            if (processReporter != null) {
                                processReporter.requestResync();
                            }
                        } else if (MessageConstants.CMD_CONFIG_UPDATE.equals(command)) {
                            Map<String, String> config = msg.getCommandConfig();
                            if (config != null) {
                                runtimeConfig.applyServerConfig(config);
                            }
                        }
                    }
                });

                client.startListening();
                if (screenEnabled) {
                    System.out.println("Screen streaming is on-demand, waiting for "
                            + MessageConstants.CMD_SCREEN_START + " command...");
                }
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
        RuntimeConfig rc = RuntimeConfig.getInstance();
        screenStreamer = new ScreenStreamer(udpSender, rc.getScreenFps(), rc.getScreenJpegQuality());
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
