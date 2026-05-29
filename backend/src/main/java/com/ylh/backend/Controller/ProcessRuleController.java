package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Dto.ProcessRuleVO;
import com.ylh.backend.Entity.Group;
import com.ylh.backend.Entity.ProcessRule;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Service.GroupService;
import com.ylh.backend.Service.ProcessRuleService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProcessRuleController {

    private final ProcessRuleService processRuleService;
    private final GroupService groupService;
    private final UserService userService;

    @GetMapping("/process-rules")
    public Result<List<ProcessRuleVO>> getAllRules() {
        Long currentUserId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(currentUserId);
        if (!roles.contains("SUPER_ADMIN") && !roles.contains("GROUP_ADMIN")) {
            return Result.error(403, "无权查看进程规则");
        }

        List<ProcessRule> rules;
        if (roles.contains("SUPER_ADMIN")) {
            rules = processRuleService.getAllRules();
        } else {
            User user = userService.getById(currentUserId);
            if (user.getGroupId() == null) {
                return Result.success(List.of());
            }
            rules = processRuleService.getRulesByGroupId(user.getGroupId());
        }

        List<ProcessRuleVO> voList = rules.stream().map(this::toVO).collect(Collectors.toList());
        return Result.success(voList);
    }

    @PostMapping("/groups/{groupId}/process-rules")
    public Result<Void> addRule(@PathVariable Long groupId, @RequestBody Map<String, String> body) {
        Long currentUserId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(currentUserId);
        if (!roles.contains("SUPER_ADMIN") && !roles.contains("GROUP_ADMIN")) {
            return Result.error(403, "无权添加进程规则");
        }

        if (roles.contains("GROUP_ADMIN")) {
            User user = userService.getById(currentUserId);
            if (!groupId.equals(user.getGroupId())) {
                return Result.error(403, "只能管理本分组的规则");
            }
        }

        Group group = groupService.getById(groupId);
        if (group == null) {
            return Result.error("分组不存在");
        }

        String processName = body.get("processName");
        String ruleType = body.get("ruleType");
        String description = body.get("description");

        if (processName == null || processName.trim().isEmpty()) {
            return Result.error("进程名不能为空");
        }
        if (ruleType == null || (!ruleType.equals("WHITELIST") && !ruleType.equals("BLACKLIST"))) {
            return Result.error("规则类型必须为 WHITELIST 或 BLACKLIST");
        }

        if (processRuleService.existsByGroupIdAndProcessName(groupId, processName)) {
            return Result.error("该进程规则已存在");
        }

        ProcessRule rule = new ProcessRule();
        rule.setGroupId(groupId);
        rule.setProcessName(processName.trim());
        rule.setRuleType(ruleType);
        rule.setDescription(description);
        processRuleService.save(rule);

        log.info("用户 {} 为分组 {} 添加了进程规则: {} ({})", currentUserId, groupId, processName, ruleType);
        return Result.success("添加成功", null);
    }

    @DeleteMapping("/groups/{groupId}/process-rules/{ruleId}")
    public Result<Void> deleteRule(@PathVariable Long groupId, @PathVariable Long ruleId) {
        Long currentUserId = getCurrentUserId();
        List<String> roles = userService.getUserRoleKeys(currentUserId);
        if (!roles.contains("SUPER_ADMIN") && !roles.contains("GROUP_ADMIN")) {
            return Result.error(403, "无权删除进程规则");
        }

        if (roles.contains("GROUP_ADMIN")) {
            User user = userService.getById(currentUserId);
            if (!groupId.equals(user.getGroupId())) {
                return Result.error(403, "只能管理本分组的规则");
            }
        }

        ProcessRule rule = processRuleService.getById(ruleId);
        if (rule == null || !rule.getGroupId().equals(groupId)) {
            return Result.error("规则不存在");
        }

        processRuleService.removeById(ruleId);
        log.info("用户 {} 删除了分组 {} 的进程规则 {}", currentUserId, groupId, ruleId);
        return Result.success("删除成功", null);
    }

    private ProcessRuleVO toVO(ProcessRule rule) {
        ProcessRuleVO vo = new ProcessRuleVO();
        vo.setId(rule.getId());
        vo.setGroupId(rule.getGroupId());
        vo.setProcessName(rule.getProcessName());
        vo.setRuleType(rule.getRuleType());
        vo.setDescription(rule.getDescription());
        vo.setCreateTime(rule.getCreateTime());

        if (rule.getGroupId() != null) {
            Group group = groupService.getById(rule.getGroupId());
            if (group != null) {
                vo.setGroupName(group.getGroupName());
            }
        }
        return vo;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
