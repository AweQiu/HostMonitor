package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Dto.GroupVO;
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
public class GroupService extends ServiceImpl<GroupMapper, Group> {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public boolean existsByGroupName(String groupName) {
        return count(new LambdaQueryWrapper<Group>().eq(Group::getGroupName, groupName)) > 0;
    }

    public int getMemberCount(Long groupId) {
        return Math.toIntExact(userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getGroupId, groupId)
        ));
    }

    public List<User> getGroupMembers(Long groupId) {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getGroupId, groupId)
        );
    }

    public User getGroupAdmin(Long groupId) {
        List<User> members = getGroupMembers(groupId);
        if (members.isEmpty()) {
            return null;
        }
        List<Long> memberIds = members.stream().map(User::getId).collect(Collectors.toList());
        Role groupAdminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, "GROUP_ADMIN")
        );
        if (groupAdminRole == null) {
            return null;
        }
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .in(UserRole::getUserId, memberIds)
                        .eq(UserRole::getRoleId, groupAdminRole.getId())
        );
        if (userRoles.isEmpty()) {
            return null;
        }
        return userMapper.selectById(userRoles.get(0).getUserId());
    }

    public GroupVO toVO(Group group) {
        GroupVO vo = new GroupVO();
        vo.setId(group.getId());
        vo.setGroupName(group.getGroupName());
        vo.setDescription(group.getDescription());
        vo.setMemberCount(getMemberCount(group.getId()));
        vo.setCreateTime(group.getCreateTime());

        User admin = getGroupAdmin(group.getId());
        if (admin != null) {
            vo.setAdminName(admin.getUsername());
            vo.setAdminId(admin.getId());
        }
        return vo;
    }

    public List<GroupVO> toVOList(List<Group> groups) {
        return groups.stream().map(this::toVO).collect(Collectors.toList());
    }
}
