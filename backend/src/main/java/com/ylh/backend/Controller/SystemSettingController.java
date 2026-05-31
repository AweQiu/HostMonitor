package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Config.AccessChecker;
import com.ylh.backend.Service.AlertSettingService;
import com.ylh.backend.Service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/system-settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final AlertSettingService alertSettingService;
    private final AccessChecker accessChecker;
    private final EmailService emailService;

    private static final Set<String> GROUP_ADMIN_READ_KEYS = Set.of(
            "client_metric_interval_ms",
            "client_process_interval_ms",
            "client_screen_fps",
            "client_screen_jpeg_quality",
            "client_process_cpu_threshold",
            "client_heartbeat_timeout_ms",
            "client_offline_threshold_ms",
            "performance_alert_enabled",
            "cpu_threshold",
            "memory_threshold",
            "disk_threshold",
            "alert_dedup_minutes",
            "process_alert_enabled"
    );

    private static final Set<String> GROUP_ADMIN_WRITE_KEYS = Set.of(
            "client_metric_interval_ms",
            "client_process_interval_ms",
            "client_screen_fps",
            "client_screen_jpeg_quality",
            "client_process_cpu_threshold",
            "client_heartbeat_timeout_ms",
            "client_offline_threshold_ms",
            "performance_alert_enabled",
            "cpu_threshold",
            "memory_threshold",
            "disk_threshold",
            "alert_dedup_minutes",
            "process_alert_enabled"
    );

    @GetMapping
    public Result<Map<String, String>> getAll() {
        Map<String, String> all = alertSettingService.getAllSettings();
        if (accessChecker.isSuperAdmin()) {
            return Result.success(all);
        }
        if (accessChecker.isGroupAdmin()) {
            Map<String, String> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : all.entrySet()) {
                if (GROUP_ADMIN_READ_KEYS.contains(entry.getKey())) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            return Result.success(filtered);
        }
        return Result.error("无权访问系统设置");
    }

    @PutMapping
    public Result<String> update(@RequestBody Map<String, String> settings) {
        if (accessChecker.isSuperAdmin()) {
            alertSettingService.updateSettings(settings);
            return Result.success("系统设置已更新");
        }
        if (accessChecker.isGroupAdmin()) {
            Map<String, String> allowed = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                if (GROUP_ADMIN_WRITE_KEYS.contains(entry.getKey())) {
                    allowed.put(entry.getKey(), entry.getValue());
                }
            }
            if (allowed.isEmpty()) {
                return Result.error("无权修改这些设置");
            }
            alertSettingService.updateSettings(allowed);
            return Result.success("设置已更新");
        }
        return Result.error("无权修改系统设置");
    }

    @PostMapping("/email/test")
    public Result<String> testEmail(@RequestBody Map<String, String> body) {
        accessChecker.requireSuperAdmin();
        String to = body.get("to");
        if (to == null || to.isBlank()) {
            return Result.error("请输入收件人邮箱");
        }
        emailService.sendTestEmail(to);
        return Result.success("测试邮件已发送");
    }
}
