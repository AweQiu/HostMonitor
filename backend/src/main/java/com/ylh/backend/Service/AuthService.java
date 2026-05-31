package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ylh.backend.Entity.Role;
import com.ylh.backend.Entity.UserRole;
import com.ylh.backend.Mapper.RoleMapper;
import com.ylh.backend.Mapper.UserRoleMapper;
import com.ylh.backend.Common.BusinessException;
import com.ylh.backend.Common.UnauthorizedException;
import com.ylh.backend.Config.JwtUtil;
import com.ylh.backend.Dto.AuthResponse;
import com.ylh.backend.Dto.LoginRequest;
import com.ylh.backend.Dto.RegisterRequest;
import com.ylh.backend.Entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AlertSettingService alertSettingService;

    public AuthResponse login(LoginRequest request) {
        User user = userService.getByUsername(request.getUsername());
        if (user == null) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new UnauthorizedException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        List<String> roles = buildUserDTO(user).getRoles();
        int sessionTimeoutHours = alertSettingService.getInt("session_timeout_hours", 24);
        long expirationMs = (long) sessionTimeoutHours * 3600 * 1000;
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roles, expirationMs);

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUser(buildUserDTO(user));
        return response;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userService.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (userService.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAgentKey(userService.generateAgentKey());
        user.setStatus(1);
        userService.save(user);

        Role userRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, "USER")
        );
        if (userRole != null) {
            UserRole userRoleRelation = new UserRole();
            userRoleRelation.setUserId(user.getId());
            userRoleRelation.setRoleId(userRole.getId());
            userRoleMapper.insert(userRoleRelation);
        }
    }

    private AuthResponse.UserDTO buildUserDTO(User user) {
        AuthResponse.UserDTO dto = new AuthResponse.UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setAvatar(user.getAvatar());
        dto.setStatus(user.getStatus());
        dto.setGroupId(user.getGroupId());
        dto.setAgentKey(user.getAgentKey());

        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId())
        );
        if (!userRoles.isEmpty()) {
            List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
            List<Role> roles = roleMapper.selectList(
                    new LambdaQueryWrapper<Role>().in(Role::getId, roleIds)
            );
            dto.setRoles(roles.stream().map(Role::getRoleKey).collect(Collectors.toList()));
        } else {
            dto.setRoles(List.of());
        }
        return dto;
    }
}
