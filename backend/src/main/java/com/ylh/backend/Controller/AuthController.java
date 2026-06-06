package com.ylh.backend.Controller;

import com.ylh.backend.Common.Result;
import com.ylh.backend.Dto.AuthResponse;
import com.ylh.backend.Dto.LoginRequest;
import com.ylh.backend.Dto.RegisterRequest;
import com.ylh.backend.Service.AuthService;
import com.ylh.backend.Service.CaptchaService;
import com.ylh.backend.Service.LoginAttemptService;
import com.ylh.backend.Service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        AuthResponse response = authService.login(request, ipAddress);
        return Result.success("登录成功", response);
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success("注册成功", null);
    }

    @GetMapping("/captcha")
    public Result<Map<String, String>> getCaptcha() {
        CaptchaService.CaptchaResult captcha = captchaService.generateCaptcha();
        return Result.success(Map.of(
                "captchaKey", captcha.getCaptchaKey(),
                "captchaImage", captcha.getCaptchaImage()
        ));
    }

    @GetMapping("/login-status")
    public Result<Map<String, Object>> getLoginStatus(HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        boolean captchaRequired = loginAttemptService.isCaptchaRequired(ipAddress);
        return Result.success(Map.of(
                "captchaRequired", captchaRequired
        ));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
