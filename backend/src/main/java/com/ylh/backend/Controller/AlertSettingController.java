package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Config.AccessChecker;
import com.ylh.backend.Service.AlertSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/alert-settings")
@RequiredArgsConstructor
public class AlertSettingController {

    private final AlertSettingService alertSettingService;
    private final AccessChecker accessChecker;

    private static final Set<String> GROUP_ADMIN_READ_KEYS = Set.of(
            "process_alert_enabled",
            "performance_alert_enabled",
            "cpu_threshold",
            "memory_threshold",
            "disk_threshold",
            "alert_dedup_minutes"
    );

    private static final Set<String> GROUP_ADMIN_WRITE_KEYS = Set.of(
            "process_alert_enabled",
            "performance_alert_enabled",
            "cpu_threshold",
            "memory_threshold",
            "disk_threshold",
            "alert_dedup_minutes"
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
        return Result.error("无权访问告警设置");
    }

    @PutMapping
    public Result<String> update(@RequestBody Map<String, String> settings) {
        if (accessChecker.isSuperAdmin()) {
            alertSettingService.updateSettings(settings);
            return Result.success("告警设置已更新");
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
            return Result.success("告警设置已更新");
        }
        return Result.error("无权修改告警设置");
    }
}
