package com.ylh.backend.Controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ylh.backend.Common.PageResult;
import com.ylh.backend.Dto.AlertVO;
import com.ylh.backend.Common.Result;
import com.ylh.backend.Config.AccessChecker;
import com.ylh.backend.Service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AccessChecker accessChecker;

    @GetMapping
    public Result<PageResult<AlertVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) Integer status) {
        Long userId = accessChecker.getCurrentUserId();
        List<String> roles = accessChecker.getCurrentUserRoles();
        Page<AlertVO> result = alertService.pageAlerts(page, size, alertType, status, userId, roles);
        return Result.success(PageResult.of(result));
    }

    @PutMapping("/{id}/resolve")
    public Result<String> resolve(@PathVariable Long id) {
        Long userId = accessChecker.getCurrentUserId();
        List<String> roles = accessChecker.getCurrentUserRoles();
        alertService.resolveAlert(id, userId, roles);
        return Result.success("告警已处理");
    }

    @GetMapping("/unresolved-count")
    public Result<Long> unresolvedCount() {
        Long userId = accessChecker.getCurrentUserId();
        List<String> roles = accessChecker.getCurrentUserRoles();
        Long count = alertService.getUnresolvedCount(userId, roles);
        return Result.success(count);
    }
}
