package com.ylh.backend.Controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ylh.backend.Common.Result;
import com.ylh.backend.Dto.*;
import com.ylh.backend.Entity.Group;
import com.ylh.backend.Entity.Role;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Entity.UserRole;
import com.ylh.backend.Mapper.RoleMapper;
import com.ylh.backend.Mapper.UserRoleMapper;
import com.ylh.backend.Service.GroupService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final UserService userService;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    @GetMapping
    public Result<List<GroupVO>> listGroups() {
        checkSuperAdminPermission();
        List<Group> groups = groupService.list(
                new LambdaQueryWrapper<Group>().orderByDesc(Group::getCreateTime)
        );
        return Result.success(groupService.toVOList(groups));
    }

    @GetMapping("/{id}")
    public Result<GroupVO> getGroup(@PathVariable Long id) {
        checkSuperAdminPermission();
        Group group = groupService.getById(id);
        if (group == null) {
            return Result.error("分组不存在");
        }
        return Result.success(groupService.toVO(group));
    }

    @PostMapping
    public Result<Void> createGroup(@RequestBody CreateGroupRequest request) {
        checkSuperAdminPermission();

        if (groupService.existsByGroupName(request.getGroupName())) {
            return Result.error("分组名称已存在");
        }

        Group group = new Group();
        group.setGroupName(request.getGroupName());
        group.setDescription(request.getDescription());
        groupService.save(group);

        if (request.getAdminId() != null) {
            assignGroupAdmin(group.getId(), request.getAdminId());
        }

        log.info("超级管理员创建了分组 {}", group.getId());
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> updateGroup(@PathVariable Long id, @RequestBody UpdateGroupRequest request) {
        checkSuperAdminPermission();

        Group group = groupService.getById(id);
        if (group == null) {
            return Result.error("分组不存在");
        }

        if (request.getGroupName() != null && !request.getGroupName().equals(group.getGroupName())) {
            if (groupService.existsByGroupName(request.getGroupName())) {
                return Result.error("分组名称已存在");
            }
            group.setGroupName(request.getGroupName());
        }
        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }
        groupService.updateById(group);

        log.info("超级管理员更新了分组 {}", id);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        checkSuperAdminPermission();

        Group group = groupService.getById(id);
        if (group == null) {
            return Result.error("分组不存在");
        }

        User admin = groupService.getGroupAdmin(id);
        if (admin != null) {
            revokeGroupAdmin(id, admin.getId());
        }

        List<User> members = groupService.getGroupMembers(id);
        for (User member : members) {
            member.setGroupId(null);
            userService.updateById(member);
        }

        groupService.removeById(id);
        log.info("超级管理员删除了分组 {}", id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/{id}/members")
    public Result<List<UserVO>> getGroupMembers(@PathVariable Long id) {
        checkSuperAdminPermission();

        Group group = groupService.getById(id);
        if (group == null) {
            return Result.error("分组不存在");
        }
        List<User> members = groupService.getGroupMembers(id);
        return Result.success(userService.toVOList(members));
    }

    @PostMapping("/{id}/members/{userId}")
    public Result<Void> addMember(@PathVariable Long id, @PathVariable Long userId) {
        checkSuperAdminPermission();

        Group group = groupService.getById(id);
        if (group == null) {
            return Result.error("分组不存在");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (id.equals(user.getGroupId())) {
            return Result.error("用户已在该分组中");
        }

        List<String> targetRoles = userService.getUserRoleKeys(userId);
        if (targetRoles.contains("SUPER_ADMIN")) {
            return Result.error("超级管理员不能加入分组");
        }

        user.setGroupId(id);
        userService.updateById(user);

        log.info("超级管理员将用户 {} 加入分组 {}", userId, id);
        return Result.success("添加成功", null);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        checkSuperAdminPermission();

        List<String> targetRoles = userService.getUserRoleKeys(userId);
        if (targetRoles.contains("SUPER_ADMIN")) {
            return Result.error("不能将超级管理员移出分组");
        }

        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!id.equals(user.getGroupId())) {
            return Result.error("用户不在该分组中");
        }

        User admin = groupService.getGroupAdmin(id);
        if (admin != null && admin.getId().equals(userId)) {
            revokeGroupAdmin(id, userId);
        }

        user.setGroupId(null);
        userService.updateById(user);

        log.info("超级管理员将用户 {} 移出分组 {}", userId, id);
        return Result.success("移出成功", null);
    }

    @PostMapping("/{id}/admin/{userId}")
    public Result<Void> assignAdmin(@PathVariable Long id, @PathVariable Long userId) {
        checkSuperAdminPermission();
        assignGroupAdmin(id, userId);
        log.info("超级管理员指定用户 {} 为分组 {} 的管理员", userId, id);
        return Result.success("指定组管理员成功", null);
    }

    @DeleteMapping("/{id}/admin/{userId}")
    public Result<Void> revokeAdmin(@PathVariable Long id, @PathVariable Long userId) {
        checkSuperAdminPermission();
        revokeGroupAdmin(id, userId);
        log.info("超级管理员撤销了用户 {} 在分组 {} 的管理员身份", userId, id);
        return Result.success("撤销组管理员成功", null);
    }

    private void assignGroupAdmin(Long groupId, Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        List<String> targetRoles = userService.getUserRoleKeys(userId);
        if (targetRoles.contains("SUPER_ADMIN")) {
            throw new RuntimeException("超级管理员不能成为组管理员");
        }

        User currentAdmin = groupService.getGroupAdmin(groupId);
        if (currentAdmin != null) {
            revokeGroupAdmin(groupId, currentAdmin.getId());
        }

        user.setGroupId(groupId);
        userService.updateById(user);

        Role userRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, "USER")
        );
        if (userRole != null) {
            userRoleMapper.delete(
                    new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getUserId, userId)
                            .eq(UserRole::getRoleId, userRole.getId())
            );
        }

        Role groupAdminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, "GROUP_ADMIN")
        );
        if (groupAdminRole != null) {
            long existingCount = userRoleMapper.selectCount(
                    new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getUserId, userId)
                            .eq(UserRole::getRoleId, groupAdminRole.getId())
            );
            if (existingCount == 0) {
                UserRole ur = new UserRole();
                ur.setUserId(userId);
                ur.setRoleId(groupAdminRole.getId());
                userRoleMapper.insert(ur);
            }
        }
    }

    private void revokeGroupAdmin(Long groupId, Long userId) {
        Role groupAdminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, "GROUP_ADMIN")
        );
        if (groupAdminRole != null) {
            userRoleMapper.delete(
                    new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getUserId, userId)
                            .eq(UserRole::getRoleId, groupAdminRole.getId())
            );
        }

        Role userRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, "USER")
        );
        if (userRole != null) {
            long hasUserRole = userRoleMapper.selectCount(
                    new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getUserId, userId)
                            .eq(UserRole::getRoleId, userRole.getId())
            );
            if (hasUserRole == 0) {
                UserRole ur = new UserRole();
                ur.setUserId(userId);
                ur.setRoleId(userRole.getId());
                userRoleMapper.insert(ur);
            }
        }
    }

    private void checkSuperAdminPermission() {
        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        if (!currentRoles.contains("SUPER_ADMIN")) {
            throw new RuntimeException("仅超级管理员可管理分组");
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
