package com.ylh.client.core;

import com.ylh.client.common.RuntimeConfig;
import com.ylh.client.network.UdpSender;
import com.ylh.client.utils.ScreenCapture;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenStreamer {

    private final UdpSender udpSender;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread streamThread;

    public ScreenStreamer(UdpSender udpSender, int fps, float quality) {
        this.udpSender = udpSender;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            streamThread = new Thread(() -> {
                RuntimeConfig rc = RuntimeConfig.getInstance();
                try {
                    udpSender.connect();
                    System.out.println("Screen streaming started (dynamic fps/quality from RuntimeConfig)");
                    System.out.println("Screen resolution: " + ScreenCapture.getScreenWidth() + "x" + ScreenCapture.getScreenHeight());

                    while (running.get()) {
                        int fps = rc.getScreenFps();
                        float quality = rc.getScreenJpegQuality();
                        long frameInterval = fps > 0 ? 1000 / fps : 200;
                        long startTime = System.currentTimeMillis();

                        byte[] frame = ScreenCapture.captureScreen(quality);
                        if (frame.length > 0) {
                            udpSender.sendFrame(frame);
                        }

                        long elapsed = System.currentTimeMillis() - startTime;
                        long sleepTime = frameInterval - elapsed;
                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        }
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Screen streaming error: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    udpSender.disconnect();
                    System.out.println("Screen streaming stopped");
                }
            }, "screen-stream");
            streamThread.setDaemon(true);
            streamThread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
