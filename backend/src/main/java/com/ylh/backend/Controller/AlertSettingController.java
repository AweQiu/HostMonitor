package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Service.AlertSettingService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alert-settings")
@RequiredArgsConstructor
public class AlertSettingController {

    private final AlertSettingService alertSettingService;
    private final UserService userService;

    @GetMapping
    public Result<Map<String, String>> getAll() {
        return Result.success(alertSettingService.getAllSettings());
    }

    @PutMapping
    public Result<String> update(@RequestBody Map<String, String> settings) {
        Long userId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(userId);
        if (!roles.contains("SUPER_ADMIN")) {
            return Result.error("仅超级管理员可修改告警设置");
        }
        alertSettingService.updateSettings(settings);
        return Result.success("告警设置已更新");
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
