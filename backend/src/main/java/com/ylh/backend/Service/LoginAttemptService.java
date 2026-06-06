package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ylh.backend.Entity.LoginAttempt;
import com.ylh.backend.Mapper.LoginAttemptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginAttemptMapper loginAttemptMapper;

    private static final int IP_RATE_LIMIT_PER_MINUTE = 10;
    private static final int IP_CAPTCHA_THRESHOLD = 3;
    private static final int ACCOUNT_LOCK_THRESHOLD = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    public boolean isIpRateLimited(String ipAddress) {
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        Long count = loginAttemptMapper.selectCount(
                new LambdaQueryWrapper<LoginAttempt>()
                        .eq(LoginAttempt::getIpAddress, ipAddress)
                        .gt(LoginAttempt::getCreateTime, oneMinuteAgo)
        );
        return count != null && count >= IP_RATE_LIMIT_PER_MINUTE;
    }

    public boolean isCaptchaRequired(String ipAddress) {
        LocalDateTime fifteenMinutesAgo = LocalDateTime.now().minusMinutes(LOCK_DURATION_MINUTES);
        Long failCount = loginAttemptMapper.selectCount(
                new LambdaQueryWrapper<LoginAttempt>()
                        .eq(LoginAttempt::getIpAddress, ipAddress)
                        .eq(LoginAttempt::getSuccess, 0)
                        .gt(LoginAttempt::getCreateTime, fifteenMinutesAgo)
        );
        return failCount != null && failCount >= IP_CAPTCHA_THRESHOLD;
    }

    public boolean isAccountLocked(String username) {
        LocalDateTime fifteenMinutesAgo = LocalDateTime.now().minusMinutes(LOCK_DURATION_MINUTES);
        Long failCount = loginAttemptMapper.selectCount(
                new LambdaQueryWrapper<LoginAttempt>()
                        .eq(LoginAttempt::getUsername, username)
                        .eq(LoginAttempt::getSuccess, 0)
                        .gt(LoginAttempt::getCreateTime, fifteenMinutesAgo)
        );
        return failCount != null && failCount >= ACCOUNT_LOCK_THRESHOLD;
    }

    public void recordAttempt(String username, String ipAddress, boolean success) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsername(username);
        attempt.setIpAddress(ipAddress);
        attempt.setSuccess(success ? 1 : 0);
        attempt.setCreateTime(LocalDateTime.now());
        loginAttemptMapper.insert(attempt);

        if (success) {
            log.info("Login success: username={}, ip={}", username, ipAddress);
        } else {
            log.warn("Login failed: username={}, ip={}", username, ipAddress);
        }
    }

    public void cleanOldAttempts(int retentionHours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(retentionHours);
        long count = loginAttemptMapper.delete(
                new LambdaQueryWrapper<LoginAttempt>()
                        .lt(LoginAttempt::getCreateTime, threshold)
        );
        if (count > 0) {
            log.info("Cleaned {} old login attempt records (retention: {} hours)", count, retentionHours);
        }
    }
}
