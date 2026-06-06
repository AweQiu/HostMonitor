package com.ylh.backend.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CaptchaService {

    private static final int CAPTCHA_WIDTH = 120;
    private static final int CAPTCHA_HEIGHT = 40;
    private static final long CAPTCHA_TTL_MS = 5 * 60 * 1000;
    private static final int MAX_CACHE_SIZE = 1000;

    private final Map<String, CaptchaEntry> captchaCache = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public CaptchaResult generateCaptcha() {
        int a = random.nextInt(50) + 1;
        int b = random.nextInt(50) + 1;
        char operator;
        int answer;

        int opType = random.nextInt(3);
        switch (opType) {
            case 0:
                operator = '+';
                answer = a + b;
                break;
            case 1:
                operator = '-';
                if (a < b) {
                    int temp = a;
                    a = b;
                    b = temp;
                }
                answer = a - b;
                break;
            default:
                operator = '×';
                a = random.nextInt(20) + 1;
                b = random.nextInt(10) + 1;
                answer = a * b;
                break;
        }

        String text = a + " " + operator + " " + b + " = ?";
        String captchaKey = java.util.UUID.randomUUID().toString().replace("-", "");

        captchaCache.put(captchaKey, new CaptchaEntry(String.valueOf(answer), System.currentTimeMillis()));

        if (captchaCache.size() > MAX_CACHE_SIZE) {
            cleanExpired();
        }

        String imageBase64 = generateCaptchaImage(text);

        CaptchaResult result = new CaptchaResult();
        result.setCaptchaKey(captchaKey);
        result.setCaptchaImage("data:image/png;base64," + imageBase64);
        return result;
    }

    public boolean validateCaptcha(String captchaKey, String captchaAnswer) {
        if (captchaKey == null || captchaAnswer == null) {
            return false;
        }

        CaptchaEntry entry = captchaCache.remove(captchaKey);
        if (entry == null) {
            return false;
        }

        if (System.currentTimeMillis() - entry.createTime > CAPTCHA_TTL_MS) {
            return false;
        }

        return entry.answer.equalsIgnoreCase(captchaAnswer.trim());
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        captchaCache.entrySet().removeIf(entry ->
                now - entry.getValue().createTime > CAPTCHA_TTL_MS
        );
    }

    private String generateCaptchaImage(String text) {
        BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);

        g2d.setColor(new Color(200, 200, 200));
        for (int i = 0; i < 6; i++) {
            int x1 = random.nextInt(CAPTCHA_WIDTH);
            int y1 = random.nextInt(CAPTCHA_HEIGHT);
            int x2 = random.nextInt(CAPTCHA_WIDTH);
            int y2 = random.nextInt(CAPTCHA_HEIGHT);
            g2d.drawLine(x1, y1, x2, y2);
        }

        for (int i = 0; i < 30; i++) {
            int x = random.nextInt(CAPTCHA_WIDTH);
            int y = random.nextInt(CAPTCHA_HEIGHT);
            g2d.setColor(new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
            g2d.fillOval(x, y, 2, 2);
        }

        g2d.setFont(new Font("Arial", Font.BOLD, 22));
        g2d.setColor(new Color(30, 30, 30));

        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = (CAPTCHA_WIDTH - textWidth) / 2;
        int y = (CAPTCHA_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();

        g2d.drawString(text, x, y);

        g2d.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate captcha image", e);
        }
    }

    public static class CaptchaResult {
        private String captchaKey;
        private String captchaImage;

        public String getCaptchaKey() { return captchaKey; }
        public void setCaptchaKey(String captchaKey) { this.captchaKey = captchaKey; }
        public String getCaptchaImage() { return captchaImage; }
        public void setCaptchaImage(String captchaImage) { this.captchaImage = captchaImage; }
    }

    private static class CaptchaEntry {
        final String answer;
        final long createTime;

        CaptchaEntry(String answer, long createTime) {
            this.answer = answer;
            this.createTime = createTime;
        }
    }
}
