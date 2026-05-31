package com.ylh.backend.Config;

import com.ylh.backend.Common.ForbiddenException;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AccessChecker {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }

    public List<String> getCurrentUserRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth.getDetails() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) auth.getDetails();
            return roles;
        }
        return userService.getUserRoleKeys(getCurrentUserId());
    }

    public boolean isSuperAdmin() {
        return getCurrentUserRoles().contains("SUPER_ADMIN");
    }

    public boolean isGroupAdmin() {
        return getCurrentUserRoles().contains("GROUP_ADMIN");
    }

    public void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new ForbiddenException("仅超级管理员可执行此操作");
        }
    }

    public void requireAdmin() {
        if (!isSuperAdmin() && !isGroupAdmin()) {
            throw new ForbiddenException("无权执行此操作");
        }
    }

    public void requireUserManagePermission(Long targetUserId) {
        if (isSuperAdmin()) return;
        if (!isGroupAdmin()) {
            throw new ForbiddenException("无权管理用户");
        }
        User currentUser = userService.getById(getCurrentUserId());
        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            throw new ForbiddenException("目标用户不存在");
        }
        if (!currentUser.getGroupId().equals(targetUser.getGroupId())) {
            throw new ForbiddenException("只能管理本组用户");
        }
    }

    public User getCurrentUser() {
        return userService.getById(getCurrentUserId());
    }
}
