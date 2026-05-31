package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Config.AccessChecker;
import com.ylh.backend.Service.AlertSettingService;
import com.ylh.backend.Service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system-settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final AlertSettingService alertSettingService;
    private final AccessChecker accessChecker;
    private final EmailService emailService;

    @GetMapping
    public Result<Map<String, String>> getAll() {
        return Result.success(alertSettingService.getAllSettings());
    }

    @PutMapping
    public Result<String> update(@RequestBody Map<String, String> settings) {
        accessChecker.requireSuperAdmin();
        alertSettingService.updateSettings(settings);
        return Result.success("系统设置已更新");
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
