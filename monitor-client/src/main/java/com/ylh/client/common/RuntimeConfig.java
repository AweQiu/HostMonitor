package com.ylh.client.common;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeConfig {

    private static final RuntimeConfig INSTANCE = new RuntimeConfig();

    private final Map<String, String> config = new ConcurrentHashMap<>();

    private RuntimeConfig() {
    }

    public static RuntimeConfig getInstance() {
        return INSTANCE;
    }

    public void initFromProperties(Properties props) {
        setDefault("metric.interval.ms", props.getProperty("metric.interval.ms", "5000"));
        setDefault("process.interval.ms", props.getProperty("process.interval.ms", "15000"));
        setDefault("screen.fps", props.getProperty("screen.fps", "5"));
        setDefault("screen.jpeg.quality", props.getProperty("screen.jpeg.quality", "0.5"));
        setDefault("process.cpu.threshold", props.getProperty("process.cpu.threshold", "1.0"));
    }

    public void applyServerConfig(Map<String, String> serverConfig) {
        if (serverConfig == null || serverConfig.isEmpty()) return;

        if (serverConfig.containsKey("client_metric_interval_ms")) {
            config.put("metric.interval.ms", serverConfig.get("client_metric_interval_ms"));
        }
        if (serverConfig.containsKey("client_process_interval_ms")) {
            config.put("process.interval.ms", serverConfig.get("client_process_interval_ms"));
        }
        if (serverConfig.containsKey("client_screen_fps")) {
            config.put("screen.fps", serverConfig.get("client_screen_fps"));
        }
        if (serverConfig.containsKey("client_screen_jpeg_quality")) {
            config.put("screen.jpeg.quality", serverConfig.get("client_screen_jpeg_quality"));
        }
        if (serverConfig.containsKey("client_process_cpu_threshold")) {
            config.put("process.cpu.threshold", serverConfig.get("client_process_cpu_threshold"));
        }
        if (serverConfig.containsKey("client_heartbeat_timeout_ms")) {
            config.put("heartbeat.timeout.ms", serverConfig.get("client_heartbeat_timeout_ms"));
        }
        if (serverConfig.containsKey("client_offline_threshold_ms")) {
            config.put("offline.threshold.ms", serverConfig.get("client_offline_threshold_ms"));
        }

        System.out.println("Runtime config updated from server: " + serverConfig.keySet());
    }

    private void setDefault(String key, String value) {
        config.putIfAbsent(key, value);
    }

    public String get(String key) {
        return config.get(key);
    }

    public long getLong(String key, long defaultValue) {
        String val = config.get(key);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        String val = config.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String val = config.get(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public float getFloat(String key, float defaultValue) {
        String val = config.get(key);
        if (val == null) return defaultValue;
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getMetricIntervalMs() {
        return getLong("metric.interval.ms", 5000);
    }

    public long getProcessIntervalMs() {
        return getLong("process.interval.ms", 15000);
    }

    public int getScreenFps() {
        return getInt("screen.fps", 5);
    }

    public float getScreenJpegQuality() {
        return getFloat("screen.jpeg.quality", 0.5f);
    }

    public double getProcessCpuThreshold() {
        return getDouble("process.cpu.threshold", 1.0);
    }
}
