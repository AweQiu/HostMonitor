package com.ylh.backend.Controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ylh.backend.Dto.AlertVO;
import com.ylh.backend.Common.Result;
import com.ylh.backend.Service.AlertService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final UserService userService;

    @GetMapping
    public Result<Page<AlertVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) Integer status) {
        Long userId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(userId);
        Page<AlertVO> result = alertService.pageAlerts(page, size, alertType, status, userId, roles);
        return Result.success(result);
    }

    @PutMapping("/{id}/resolve")
    public Result<String> resolve(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(userId);
        alertService.resolveAlert(id, userId, roles);
        return Result.success("告警已处理");
    }

    @GetMapping("/unresolved-count")
    public Result<Long> unresolvedCount() {
        Long userId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(userId);
        Long count = alertService.getUnresolvedCount(userId, roles);
        return Result.success(count);
    }

    private Long getCurrentUserId() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
