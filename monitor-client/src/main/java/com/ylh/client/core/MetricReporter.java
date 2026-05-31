package com.ylh.client.core;

import com.ylh.client.common.RuntimeConfig;
import com.ylh.client.network.NetworkClient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MetricReporter {

    private final NetworkClient client;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread reporterThread;

    public MetricReporter(NetworkClient client) {
        this.client = client;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            reporterThread = new Thread(() -> {
                RuntimeConfig rc = RuntimeConfig.getInstance();
                System.out.println("Metric reporter started (interval: " + rc.getMetricIntervalMs() + "ms, dynamic)");
                try {
                    while (running.get()) {
                        client.sendMetric();
                        Thread.sleep(rc.getMetricIntervalMs());
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Metric report error: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Metric reporter stopped");
            }, "metric-reporter");
            reporterThread.setDaemon(true);
            reporterThread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (reporterThread != null) {
            reporterThread.interrupt();
            reporterThread = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
