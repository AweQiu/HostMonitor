package com.ylh.client.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ScreenCapture {

    private static final int MAX_WIDTH = 1280;
    private static final int TARGET_SIZE = 45000;

    private static final Robot robot;
    private static final Rectangle screenRect;
    private static final ImageWriter jpegWriter;
    private static final ImageWriteParam writeParam;

    static {
        try {
            robot = new Robot();
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();
            Rectangle totalBounds = new Rectangle();
            for (GraphicsDevice screen : screens) {
                GraphicsConfiguration config = screen.getDefaultConfiguration();
                Rectangle bounds = config.getBounds();
                totalBounds = totalBounds.union(bounds);
            }
            screenRect = totalBounds;
        } catch (AWTException e) {
            throw new RuntimeException("Failed to initialize Robot for screen capture", e);
        }

        jpegWriter = ImageIO.getImageWritersByFormatName("jpg").next();
        writeParam = jpegWriter.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    }

    private ScreenCapture() {
    }

    public static byte[] captureScreen() {
        return captureScreen(0.5f);
    }

    public static byte[] captureScreen(float quality) {
        BufferedImage screenshot = robot.createScreenCapture(screenRect);
        BufferedImage scaled = scaleDown(screenshot);
        screenshot.flush();

        float actualQuality = quality;
        byte[] result = compress(scaled, actualQuality);

        if (result.length > TARGET_SIZE && actualQuality > 0.2f) {
            actualQuality = actualQuality * TARGET_SIZE / result.length;
            if (actualQuality < 0.15f) actualQuality = 0.15f;
            result = compress(scaled, actualQuality);
        }

        return result;
    }

    private static BufferedImage scaleDown(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= MAX_WIDTH) {
            return src;
        }
        int newW = MAX_WIDTH;
        int newH = (int) ((double) h / w * MAX_WIDTH);
        BufferedImage dest = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return dest;
    }

    private static byte[] compress(BufferedImage image, float quality) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writeParam.setCompressionQuality(quality);
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            jpegWriter.setOutput(ios);
            jpegWriter.write(null, new IIOImage(image, null, null), writeParam);
            ios.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            System.err.println("Failed to capture screen: " + e.getMessage());
            return new byte[0];
        }
    }

    public static int getScreenWidth() {
        return screenRect.width;
    }

    public static int getScreenHeight() {
        return screenRect.height;
    }
}
