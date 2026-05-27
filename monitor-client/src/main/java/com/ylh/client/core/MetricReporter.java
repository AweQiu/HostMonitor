package com.ylh.client.core;

import com.ylh.client.network.NetworkClient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 性能指标上报器
 * <p>
 * 在独立线程中按固定间隔采集主机性能指标，通过 TCP 连接发送给服务器。
 * 通过 {@link #start()} 启动采集循环，{@link #stop()} 停止。
 * </p>
 */
public class MetricReporter {

    private final NetworkClient client;
    private final long intervalMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread reporterThread;

    public MetricReporter(NetworkClient client, long intervalMs) {
        this.client = client;
        this.intervalMs = intervalMs;
    }

    /**
     * 启动指标采集循环
     * <p>
     * 在守护线程中每 {@code intervalMs} 毫秒采集一次性能指标并发送。
     * 如果已在运行则不会重复启动。
     * </p>
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            reporterThread = new Thread(() -> {
                System.out.println("Metric reporter started (interval: " + intervalMs + "ms)");
                try {
                    while (running.get()) {
                        client.sendMetric();
                        Thread.sleep(intervalMs);
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

    /**
     * 停止指标采集循环
     */
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
