package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Config.AccessChecker;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Service.GroupSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/group-settings")
@RequiredArgsConstructor
public class GroupSettingController {

    private final GroupSettingService groupSettingService;
    private final AccessChecker accessChecker;

    @GetMapping("/{groupId}")
    public Result<Map<String, String>> getGroupSettings(@PathVariable Long groupId) {
        if (accessChecker.isSuperAdmin()) {
            return Result.success(groupSettingService.getEffectiveSettings(groupId));
        }
        if (accessChecker.isGroupAdmin()) {
            User currentUser = accessChecker.getCurrentUser();
            if (!groupId.equals(currentUser.getGroupId())) {
                return Result.error("只能查看本组设置");
            }
            return Result.success(groupSettingService.getEffectiveSettings(groupId));
        }
        return Result.error("无权访问组设置");
    }

    @PutMapping("/{groupId}")
    public Result<String> updateGroupSettings(@PathVariable Long groupId,
                                               @RequestBody Map<String, String> settings) {
        if (accessChecker.isSuperAdmin()) {
            groupSettingService.updateGroupSettings(groupId, settings);
            return Result.success("组设置已更新");
        }
        if (accessChecker.isGroupAdmin()) {
            User currentUser = accessChecker.getCurrentUser();
            if (!groupId.equals(currentUser.getGroupId())) {
                return Result.error("只能修改本组设置");
            }
            groupSettingService.updateGroupSettings(groupId, settings);
            return Result.success("组设置已更新");
        }
        return Result.error("无权修改组设置");
    }
}
