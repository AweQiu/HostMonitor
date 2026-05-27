package com.ylh.client.core;

import com.ylh.client.network.UdpSender;
import com.ylh.client.utils.ScreenCapture;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 屏幕截图流发送器
 * <p>
 * 在独立线程中按指定帧率截取屏幕，通过 UDP 发送给服务器。
 * 通过 {@link #start()} 启动截图流，{@link #stop()} 停止。
 * </p>
 */
public class ScreenStreamer {

    private final UdpSender udpSender;
    private final int fps;
    private final float quality;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread streamThread;

    public ScreenStreamer(UdpSender udpSender, int fps, float quality) {
        this.udpSender = udpSender;
        this.fps = fps;
        this.quality = quality;
    }

    /**
     * 启动屏幕截图流
     * <p>
     * 在守护线程中按指定帧率截屏并通过 UDP 发送。
     * 如果已在运行则不会重复启动。
     * </p>
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            streamThread = new Thread(() -> {
                try {
                    udpSender.connect();
                    System.out.println("Screen streaming started (" + fps + " fps, quality " + quality + ")");
                    System.out.println("Screen resolution: " + ScreenCapture.getScreenWidth() + "x" + ScreenCapture.getScreenHeight());

                    long frameInterval = 1000 / fps;

                    while (running.get()) {
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

    /**
     * 停止屏幕截图流
     */
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
