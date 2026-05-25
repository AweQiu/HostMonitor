package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Dto.UserVO;
import com.ylh.backend.Entity.Group;
import com.ylh.backend.Entity.Role;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Entity.UserRole;
import com.ylh.backend.Mapper.GroupMapper;
import com.ylh.backend.Mapper.RoleMapper;
import com.ylh.backend.Mapper.UserMapper;
import com.ylh.backend.Mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final GroupMapper groupMapper;

    public User getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    public User getByEmail(String email) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    public boolean existsByUsername(String username) {
        return count(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }

    public boolean existsByEmail(String email) {
        return count(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }

    public Page<User> pageByRole(Long groupId, String roleKey, String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (groupId != null) {
            wrapper.eq(User::getGroupId, groupId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getEmail, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);

        if (roleKey != null && !roleKey.isBlank()) {
            Role role = roleMapper.selectOne(
                    new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, roleKey)
            );
            if (role != null) {
                List<UserRole> userRoles = userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, role.getId())
                );
                List<Long> userIds = userRoles.stream().map(UserRole::getUserId).collect(Collectors.toList());
                if (userIds.isEmpty()) {
                    return new Page<>(pageNum, pageSize, 0);
                }
                wrapper.in(User::getId, userIds);
            } else {
                return new Page<>(pageNum, pageSize, 0);
            }
        }

        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public List<String> getUserRoleKeys(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>().in(Role::getId, roleIds)
        );
        return roles.stream().map(Role::getRoleKey).collect(Collectors.toList());
    }

    public UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setGroupId(user.getGroupId());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoles(getUserRoleKeys(user.getId()));

        if (user.getGroupId() != null) {
            Group group = groupMapper.selectById(user.getGroupId());
            if (group != null) {
                vo.setGroupName(group.getGroupName());
            }
        }
        return vo;
    }

    public List<UserVO> toVOList(List<User> users) {
        return users.stream().map(this::toVO).collect(Collectors.toList());
    }
}
