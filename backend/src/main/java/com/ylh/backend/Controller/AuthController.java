package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Dto.AuthResponse;
import com.ylh.backend.Dto.LoginRequest;
import com.ylh.backend.Dto.RegisterRequest;
import com.ylh.backend.Service.AuthService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return Result.success("登录成功", response);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success("注册成功", null);
    }

    @GetMapping("/agent-key")
    public Result<Map<String, String>> getAgentKey() {
        Long userId = getCurrentUserId();
        com.ylh.backend.Entity.User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(Map.of("agentKey", user.getAgentKey()));
    }

    @PostMapping("/agent-key/reset")
    public Result<Map<String, String>> resetAgentKey() {
        Long userId = getCurrentUserId();
        String newKey = userService.resetAgentKey(userId);
        return Result.success("密钥已重置", Map.of("agentKey", newKey));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
