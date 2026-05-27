package com.ylh.backend.Controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ylh.backend.Common.Result;
import com.ylh.backend.Dto.*;
import com.ylh.backend.Entity.Role;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Entity.UserRole;
import com.ylh.backend.Entity.Group;
import com.ylh.backend.Mapper.RoleMapper;
import com.ylh.backend.Mapper.UserRoleMapper;
import com.ylh.backend.Service.GroupService;
import com.ylh.backend.Service.UserService;
import com.ylh.backend.Socket.SocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final GroupService groupService;
    private final SocketServer socketServer;

    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        Long userId = getCurrentUserId();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(userService.toVO(user));
    }

    @GetMapping("/roles")
    public Result<List<Role>> listRoles() {
        checkAdminPermission();
        return Result.success(roleMapper.selectList(null));
    }

    @GetMapping("/groups")
    public Result<List<Group>> listGroups() {
        checkAdminPermission();
        return Result.success(groupService.list());
    }

    @GetMapping
    public Result<Page<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) Long groupId) {
        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        User currentUser = userService.getById(currentUserId);

        Long filterGroupId = null;
        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = currentRoles.contains("GROUP_ADMIN");

        if (!isSuperAdmin && !isGroupAdmin) {
            return Result.error(403, "无权访问用户管理");
        }

        if (isGroupAdmin && !isSuperAdmin) {
            if (currentUser.getGroupId() == null) {
                return Result.error(403, "您未分配到任何组，无法管理用户");
            }
            filterGroupId = currentUser.getGroupId();
        } else if (isSuperAdmin && groupId != null) {
            filterGroupId = groupId;
        }

        Page<User> userPage = userService.pageByRole(filterGroupId, roleKey, keyword, page, size);
        Page<UserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userService.toVOList(userPage.getRecords()));
        return Result.success(voPage);
    }

    @PostMapping
    public Result<Void> createUser(@RequestBody CreateUserRequest request) {
        checkAdminPermission();

        if (userService.existsByUsername(request.getUsername())) {
            return Result.error("用户名已存在");
        }
        if (userService.existsByEmail(request.getEmail())) {
            return Result.error("邮箱已被注册");
        }

        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = currentRoles.contains("GROUP_ADMIN");

        if ("SUPER_ADMIN".equals(request.getRoleKey())) {
            return Result.error("不可创建超级管理员");
        }

        if (isGroupAdmin && !isSuperAdmin) {
            User currentUser = userService.getById(currentUserId);
            request.setGroupId(currentUser.getGroupId());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAgentKey(userService.generateAgentKey());
        user.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        user.setGroupId(request.getGroupId());
        userService.save(user);

        String roleKey = request.getRoleKey() != null ? request.getRoleKey() : "USER";
        assignRoleToUser(user.getId(), roleKey);

        log.info("用户 {} 创建了新用户 {}", currentUserId, user.getId());
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        checkUserManagePermission(id);

        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");

        if (isSuperAdmin && request.getClearGroupId() != null && request.getClearGroupId()) {
            List<String> targetRoles = userService.getUserRoleKeys(id);
            if (targetRoles.contains("SUPER_ADMIN")) {
                return Result.error("不能将超级管理员移出分组");
            }
            if (targetRoles.contains("GROUP_ADMIN")) {
                Long oldGroupId = user.getGroupId();
                if (oldGroupId != null) {
                    groupService.revokeGroupAdminDirect(oldGroupId, id);
                }
            }
            user.setGroupId(null);
        } else if (isSuperAdmin && request.getGroupId() != null) {
            Long newGroupId = request.getGroupId();
            Long oldGroupId = user.getGroupId();

            List<String> targetRoles = userService.getUserRoleKeys(id);
            if (targetRoles.contains("SUPER_ADMIN")) {
                return Result.error("超级管理员不能加入分组");
            }

            if (!newGroupId.equals(oldGroupId)) {
                if (targetRoles.contains("GROUP_ADMIN") && oldGroupId != null) {
                    groupService.revokeGroupAdminDirect(oldGroupId, id);
                    assignRoleToUser(id, "USER");
                }
            }
            user.setGroupId(newGroupId);
        }

        userService.updateById(user);
        log.info("用户 {} 更新了用户 {} 的信息", currentUserId, id);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        checkUserManagePermission(id);

        Long currentUserId = getCurrentUserId();
        if (currentUserId.equals(id)) {
            return Result.error("不能删除自己");
        }

        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }

        List<String> targetRoles = userService.getUserRoleKeys(id);
        if (targetRoles.contains("SUPER_ADMIN")) {
            return Result.error("不能删除超级管理员");
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        userService.removeById(id);
        log.info("用户 {} 删除了用户 {}", currentUserId, id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/role")
    public Result<Void> assignRole(@PathVariable Long id, @RequestBody AssignRoleRequest request) {
        checkAdminPermission();

        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");

        if ("SUPER_ADMIN".equals(request.getRoleKey())) {
            return Result.error("不可分配超级管理员角色");
        }

        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if ("GROUP_ADMIN".equals(request.getRoleKey())) {
            if (user.getGroupId() == null) {
                return Result.error("请先将该用户加入一个分组，再指定为组管理员");
            }
            User existingAdmin = groupService.getGroupAdmin(user.getGroupId());
            if (existingAdmin != null && !existingAdmin.getId().equals(id)) {
                return Result.error("该分组已有组管理员「" + existingAdmin.getUsername() + "」，请先撤销其管理员身份");
            }
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        assignRoleToUser(id, request.getRoleKey());

        log.info("用户 {} 将用户 {} 的角色变更为 {}", currentUserId, id, request.getRoleKey());
        return Result.success("角色分配成功", null);
    }

    @PostMapping("/{id}/reset-agent-key")
    public Result<String> resetAgentKey(@PathVariable Long id) {
        checkUserManagePermission(id);

        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");

        if (!isSuperAdmin) {
            return Result.error(403, "只有超级管理员可以重置密钥");
        }

        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }

        socketServer.sendCommandToUser(id, "DISCONNECT");
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        socketServer.kickExistingConnection(id, -1L);

        String newKey = userService.resetAgentKey(id);
        log.info("用户 {} 重置了用户 {} 的 agent-key", currentUserId, id);
        return Result.success("密钥已重置", newKey);
    }

    private void assignRoleToUser(Long userId, String roleKey) {
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, roleKey)
        );
        if (role != null) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRoleMapper.insert(userRole);
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }

    private void checkAdminPermission() {
        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        if (!currentRoles.contains("SUPER_ADMIN") && !currentRoles.contains("GROUP_ADMIN")) {
            throw new RuntimeException("无权执行此操作");
        }
    }

    private void checkUserManagePermission(Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = currentRoles.contains("GROUP_ADMIN");

        if (!isSuperAdmin && !isGroupAdmin) {
            throw new RuntimeException("无权管理用户");
        }

        if (isGroupAdmin && !isSuperAdmin) {
            User currentUser = userService.getById(currentUserId);
            User targetUser = userService.getById(targetUserId);
            if (targetUser == null) {
                throw new RuntimeException("目标用户不存在");
            }
            if (!currentUser.getGroupId().equals(targetUser.getGroupId())) {
                throw new RuntimeException("只能管理本组用户");
            }
        }
    }
}
