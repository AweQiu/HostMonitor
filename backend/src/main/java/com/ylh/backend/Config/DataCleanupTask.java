package com.ylh.backend.Config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ylh.backend.Entity.HostMetric;
import com.ylh.backend.Entity.HostProcess;
import com.ylh.backend.Entity.Notification;
import com.ylh.backend.Entity.UserNotification;
import com.ylh.backend.Mapper.HostMetricMapper;
import com.ylh.backend.Mapper.HostProcessMapper;
import com.ylh.backend.Mapper.NotificationMapper;
import com.ylh.backend.Mapper.UserNotificationMapper;
import com.ylh.backend.Service.AlertSettingService;
import com.ylh.backend.Service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupTask {

    private final AlertSettingService alertSettingService;
    private final HostMetricMapper hostMetricMapper;
    private final HostProcessMapper hostProcessMapper;
    private final NotificationMapper notificationMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final LoginAttemptService loginAttemptService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredData() {
        int retentionDays = alertSettingService.getInt("data_retention_days", 2);
        LocalDateTime dataThreshold = LocalDateTime.now().minusDays(retentionDays);

        long metricCount = cleanMetrics(dataThreshold);
        long processCount = cleanProcesses(dataThreshold);

        if (metricCount > 0 || processCount > 0) {
            log.info("数据清理完成: 清理 {} 条指标数据, {} 条进程数据 (保留 {} 天)", metricCount, processCount, retentionDays);
        }

        int notificationRetentionDays = alertSettingService.getInt("notification_retention_days", 30);
        LocalDateTime notifThreshold = LocalDateTime.now().minusDays(notificationRetentionDays);
        long notifCount = cleanNotifications(notifThreshold);

        if (notifCount > 0) {
            log.info("通知清理完成: 清理 {} 条过期通知 (保留 {} 天)", notifCount, notificationRetentionDays);
        }

        loginAttemptService.cleanOldAttempts(24);
    }

    private long cleanMetrics(LocalDateTime threshold) {
        try {
            return hostMetricMapper.delete(
                    new LambdaQueryWrapper<HostMetric>()
                            .lt(HostMetric::getCollectTime, threshold)
            );
        } catch (Exception e) {
            log.error("清理指标数据失败", e);
            return 0;
        }
    }

    private long cleanProcesses(LocalDateTime threshold) {
        try {
            return hostProcessMapper.delete(
                    new LambdaQueryWrapper<HostProcess>()
                            .lt(HostProcess::getCollectTime, threshold)
            );
        } catch (Exception e) {
            log.error("清理进程数据失败", e);
            return 0;
        }
    }

    @Transactional
    public long cleanNotifications(LocalDateTime threshold) {
        try {
            List<Notification> expired = notificationMapper.selectList(
                    new LambdaQueryWrapper<Notification>()
                            .lt(Notification::getSendTime, threshold)
            );
            if (expired.isEmpty()) return 0;

            List<Long> expiredIds = expired.stream().map(Notification::getId).toList();
            userNotificationMapper.delete(
                    new LambdaQueryWrapper<UserNotification>()
                            .in(UserNotification::getNotificationId, expiredIds)
            );
            notificationMapper.deleteBatchIds(expiredIds);
            return expiredIds.size();
        } catch (Exception e) {
            log.error("清理通知数据失败", e);
            return 0;
        }
    }
}
