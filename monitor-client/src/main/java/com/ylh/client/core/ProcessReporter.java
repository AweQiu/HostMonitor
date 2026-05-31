package com.ylh.client.core;

import com.ylh.client.common.RuntimeConfig;
import com.ylh.client.common.SocketMessage;
import com.ylh.client.models.ProcessInfo;
import com.ylh.client.models.ProcessListDelta;
import com.ylh.client.network.NetworkClient;
import com.ylh.client.utils.ProcessCollector;
import com.ylh.client.utils.ProcessDeltaCalculator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessReporter {

    private final NetworkClient client;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger seq = new AtomicInteger(0);
    private final Map<Integer, ProcessInfo> lastSnapshot = new HashMap<>();
    private volatile boolean needFullSync = true;
    private Thread reporterThread;

    public ProcessReporter(NetworkClient client) {
        this.client = client;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            reporterThread = new Thread(() -> {
                RuntimeConfig rc = RuntimeConfig.getInstance();
                System.out.println("Process reporter started (interval: " + rc.getProcessIntervalMs() + "ms, mode: incremental, dynamic)");
                try {
                    while (running.get()) {
                        List<ProcessInfo> processList = ProcessCollector.collectProcessList();
                        if (needFullSync) {
                            sendFull(processList);
                        } else {
                            sendDelta(processList);
                        }
                        Thread.sleep(rc.getProcessIntervalMs());
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Process report error: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Process reporter stopped");
            }, "process-reporter");
            reporterThread.setDaemon(true);
            reporterThread.start();
        }
    }

    private void sendFull(List<ProcessInfo> processList) throws IOException {
        int currentSeq = seq.incrementAndGet();
        SocketMessage msg = SocketMessage.processListFull(processList);
        client.send(msg.serialize().getBytes(StandardCharsets.UTF_8));

        lastSnapshot.clear();
        for (ProcessInfo p : processList) {
            lastSnapshot.put(p.getPid(), p);
        }
        needFullSync = false;
        System.out.println("Sent full process list: seq=" + currentSeq + ", count=" + processList.size());
    }

    private void sendDelta(List<ProcessInfo> processList) throws IOException {
        int currentSeq = seq.incrementAndGet();
        RuntimeConfig rc = RuntimeConfig.getInstance();
        ProcessListDelta delta = ProcessDeltaCalculator.compute(lastSnapshot, processList, currentSeq, rc.getProcessCpuThreshold());

        Map<Integer, ProcessInfo> currentMap = new HashMap<>();
        for (ProcessInfo p : processList) {
            currentMap.put(p.getPid(), p);
        }
        lastSnapshot.clear();
        lastSnapshot.putAll(currentMap);

        SocketMessage msg = SocketMessage.processListDelta(delta);
        client.send(msg.serialize().getBytes(StandardCharsets.UTF_8));

        int changeCount = delta.getAdded().size() + delta.getRemoved().size() + delta.getChanged().size();
        System.out.println("Sent delta process list: seq=" + currentSeq
                + ", added=" + delta.getAdded().size()
                + ", removed=" + delta.getRemoved().size()
                + ", changed=" + delta.getChanged().size()
                + ", total=" + processList.size());
    }

    public void requestResync() {
        needFullSync = true;
        System.out.println("Process resync requested, next report will be full");
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
