package com.ylh.backend.Service;

import com.ylh.backend.Common.BusinessException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final AlertSettingService alertSettingService;

    public boolean isEmailEnabled() {
        return "true".equals(alertSettingService.get("email_enabled"));
    }

    public JavaMailSender buildMailSender() {
        String host = alertSettingService.get("smtp_host");
        String port = alertSettingService.get("smtp_port");
        String username = alertSettingService.get("smtp_username");
        String password = alertSettingService.get("smtp_password");

        if (host == null || host.isBlank() || username == null || username.isBlank()) {
            return null;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(Integer.parseInt(port != null ? port : "465"));
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.starttls.enable", "false");
        props.put("mail.smtp.ssl.trust", host);

        return sender;
    }

    public void sendTestEmail(String to) {
        JavaMailSender sender = buildMailSender();
        if (sender == null) {
            throw new BusinessException("邮件配置不完整，请先配置SMTP信息");
        }

        try {
            String from = alertSettingService.get("smtp_username");
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("HostMonitor 测试邮件");
            helper.setText("这是一封来自 HostMonitor 主机监控系统的测试邮件，如果您收到了此邮件，说明邮件配置正确。", true);
            sender.send(message);
            log.info("测试邮件发送成功: {}", to);
        } catch (Exception e) {
            log.error("测试邮件发送失败: {}", e.getMessage(), e);
            throw new BusinessException("邮件发送失败: " + e.getMessage());
        }
    }

    @Async
    public void sendAlertEmailAsync(List<String> toList, String subject, String content) {
        if (!isEmailEnabled()) return;
        if (toList == null || toList.isEmpty()) return;

        JavaMailSender sender = buildMailSender();
        if (sender == null) {
            log.warn("邮件配置不完整，跳过邮件发送");
            return;
        }

        try {
            String from = alertSettingService.get("smtp_username");
            for (String to : toList) {
                try {
                    MimeMessage message = sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                    helper.setFrom(from);
                    helper.setTo(to);
                    helper.setSubject(subject);
                    helper.setText(content, true);
                    sender.send(message);
                } catch (Exception e) {
                    log.warn("发送告警邮件到 {} 失败: {}", to, e.getMessage());
                }
            }
            log.info("告警邮件发送完成: {} 封", toList.size());
        } catch (Exception e) {
            log.error("告警邮件发送异常", e);
        }
    }
}
