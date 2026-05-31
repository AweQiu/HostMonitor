package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Common.ForbiddenException;
import com.ylh.backend.Common.NotFoundException;
import com.ylh.backend.Dto.AlertVO;
import com.ylh.backend.Entity.*;
import com.ylh.backend.Mapper.AlertRecordMapper;
import com.ylh.backend.Mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService extends ServiceImpl<AlertRecordMapper, AlertRecord> {

    private static final long DEDUP_MINUTES = 30;

    private final AlertSettingService alertSettingService;
    private final ProcessRuleService processRuleService;
    private final NotificationService notificationService;
    private final HostInfoService hostInfoService;
    private final UserService userService;
    private final UserRoleMapper userRoleMapper;
    private final EmailService emailService;

    @Transactional
    public void checkProcessAlerts(Long hostId, Map<Integer, HostProcess> processSnapshot) {
        if (!alertSettingService.getBoolean("process_alert_enabled", true)) return;

        HostInfo host = hostInfoService.getById(hostId);
        if (host == null) return;

        User hostUser = userService.getById(host.getUserId());
        if (hostUser == null || hostUser.getGroupId() == null) return;

        Long groupId = hostUser.getGroupId();

        List<ProcessRule> blacklist = processRuleService.getBlacklistByGroupId(groupId);
        List<ProcessRule> whitelist = processRuleService.getWhitelistByGroupId(groupId);

        if (blacklist.isEmpty() && whitelist.isEmpty()) return;

        Set<String> runningProcessNames = processSnapshot.values().stream()
                .map(HostProcess::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (ProcessRule rule : blacklist) {
            if (runningProcessNames.contains(rule.getProcessName())) {
                createAlertIfNotDuplicate(hostId, "BLACKLIST_RUNNING", rule.getProcessName(), rule.getId(), host, hostUser, groupId);
            }
        }

        for (ProcessRule rule : whitelist) {
            if (!runningProcessNames.contains(rule.getProcessName())) {
                createAlertIfNotDuplicate(hostId, "WHITELIST_MISSING", rule.getProcessName(), rule.getId(), host, hostUser, groupId);
            }
        }
    }

    @Transactional
    public void checkPerformanceAlerts(Long hostId, HostMetric metric) {
        if (!alertSettingService.getBoolean("performance_alert_enabled", true)) return;

        HostInfo host = hostInfoService.getById(hostId);
        if (host == null) return;

        User hostUser = userService.getById(host.getUserId());
        if (hostUser == null) return;

        Long groupId = hostUser.getGroupId();

        double cpuThreshold = alertSettingService.getDouble("cpu_threshold", 90);
        double memoryThreshold = alertSettingService.getDouble("memory_threshold", 90);
        double diskThreshold = alertSettingService.getDouble("disk_threshold", 90);

        if (metric.getCpuUsage() != null
                && metric.getCpuUsage().doubleValue() > cpuThreshold) {
            String detail = String.format("CPU: %.1f%% (阈值: %.0f%%)", metric.getCpuUsage().doubleValue(), cpuThreshold);
            createPerformanceAlertIfNotDuplicate(hostId, "CPU_HIGH", "CPU", host, hostUser, groupId, detail);
        }

        if (metric.getMemoryUsage() != null
                && metric.getMemoryUsage().doubleValue() > memoryThreshold) {
            String detail = String.format("内存: %.1f%% (阈值: %.0f%%)", metric.getMemoryUsage().doubleValue(), memoryThreshold);
            createPerformanceAlertIfNotDuplicate(hostId, "MEMORY_HIGH", "Memory", host, hostUser, groupId, detail);
        }

        if (metric.getDiskUsage() != null
                && metric.getDiskUsage().doubleValue() > diskThreshold) {
            String detail = String.format("磁盘: %.1f%% (阈值: %.0f%%)", metric.getDiskUsage().doubleValue(), diskThreshold);
            createPerformanceAlertIfNotDuplicate(hostId, "DISK_HIGH", "Disk", host, hostUser, groupId, detail);
        }
    }

    private void createPerformanceAlertIfNotDuplicate(Long hostId, String alertType, String metricName,
                                                       HostInfo host, User hostUser, Long groupId, String detail) {
        int dedupMinutes = alertSettingService.getInt("alert_dedup_minutes", 30);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(dedupMinutes);
        long existing = count(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getHostId, hostId)
                .eq(AlertRecord::getAlertType, alertType)
                .eq(AlertRecord::getProcessName, metricName)
                .eq(AlertRecord::getStatus, 0)
                .ge(AlertRecord::getCreateTime, cutoff));

        if (existing > 0) {
            log.debug("Alert dedup: hostId={}, type={}, metric={} already has recent alert", hostId, alertType, metricName);
            return;
        }

        String title;
        if ("CPU_HIGH".equals(alertType)) {
            title = "[性能告警] CPU 使用率过高";
        } else if ("MEMORY_HIGH".equals(alertType)) {
            title = "[性能告警] 内存使用率过高";
        } else {
            title = "[性能告警] 磁盘使用率过高";
        }

        String content = String.format("主机 %s (%s) %s\n检测时间: %s",
                host.getHostname(), host.getIpAddress(), detail, LocalDateTime.now());

        List<Long> targetUserIds = new ArrayList<>();
        targetUserIds.add(hostUser.getId());

        if (groupId != null) {
            List<User> groupAdmins = findGroupAdmins(groupId);
            for (User admin : groupAdmins) {
                if (!targetUserIds.contains(admin.getId())) {
                    targetUserIds.add(admin.getId());
                }
            }
        }

        Long notificationId = notificationService.sendSystemNotification(title, content, targetUserIds);

        sendAlertEmailAsync(targetUserIds, title, content);

        AlertRecord record = new AlertRecord();
        record.setHostId(hostId);
        record.setAlertType(alertType);
        record.setProcessName(metricName);
        record.setRuleId(null);
        record.setNotificationId(notificationId);
        record.setStatus(0);
        save(record);

        log.info("Created performance alert: hostId={}, type={}, metric={}, notificationId={}", hostId, alertType, metricName, notificationId);
    }

    private void createAlertIfNotDuplicate(Long hostId, String alertType, String processName,
                                            Long ruleId, HostInfo host, User hostUser, Long groupId) {
        int dedupMinutes = alertSettingService.getInt("alert_dedup_minutes", 30);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(dedupMinutes);
        long existing = count(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getHostId, hostId)
                .eq(AlertRecord::getAlertType, alertType)
                .eq(AlertRecord::getProcessName, processName)
                .eq(AlertRecord::getStatus, 0)
                .ge(AlertRecord::getCreateTime, cutoff));

        if (existing > 0) {
            log.debug("Alert dedup: hostId={}, type={}, process={} already has recent alert", hostId, alertType, processName);
            return;
        }

        String title;
        String content;
        if ("BLACKLIST_RUNNING".equals(alertType)) {
            title = "[进程告警] 检测到黑名单进程运行";
            content = String.format("主机 %s (%s) 正在运行黑名单进程: %s\n检测时间: %s",
                    host.getHostname(), host.getIpAddress(), processName, LocalDateTime.now());
        } else {
            title = "[进程告警] 白名单进程未运行";
            content = String.format("主机 %s (%s) 未运行白名单进程: %s\n检测时间: %s",
                    host.getHostname(), host.getIpAddress(), processName, LocalDateTime.now());
        }

        List<Long> targetUserIds = new ArrayList<>();
        targetUserIds.add(hostUser.getId());

        List<User> groupAdmins = findGroupAdmins(groupId);
        for (User admin : groupAdmins) {
            if (!targetUserIds.contains(admin.getId())) {
                targetUserIds.add(admin.getId());
            }
        }

        Long notificationId = notificationService.sendSystemNotification(title, content, targetUserIds);

        sendAlertEmailAsync(targetUserIds, title, content);

        AlertRecord record = new AlertRecord();
        record.setHostId(hostId);
        record.setAlertType(alertType);
        record.setProcessName(processName);
        record.setRuleId(ruleId);
        record.setNotificationId(notificationId);
        record.setStatus(0);
        save(record);

        log.info("Created alert: hostId={}, type={}, process={}, notificationId={}", hostId, alertType, processName, notificationId);
    }

    private void sendAlertEmailAsync(List<Long> targetUserIds, String title, String content) {
        if (!emailService.isEmailEnabled()) return;
        List<String> emails = targetUserIds.stream()
                .map(id -> userService.getById(id))
                .filter(Objects::nonNull)
                .map(User::getEmail)
                .filter(Objects::nonNull)
                .filter(e -> !e.isBlank())
                .collect(Collectors.toList());
        if (!emails.isEmpty()) {
            emailService.sendAlertEmailAsync(emails, title, content.replace("\n", "<br>"));
        }
    }

    private List<User> findGroupAdmins(Long groupId) {
        List<User> groupMembers = userService.list(
                new LambdaQueryWrapper<User>()
                        .eq(User::getGroupId, groupId)
                        .eq(User::getStatus, 1)
        );
        if (groupMembers.isEmpty()) return List.of();

        List<Long> memberIds = groupMembers.stream().map(User::getId).collect(Collectors.toList());
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, memberIds)
        );

        Set<Long> groupAdminRoleIds = new HashSet<>();
        for (UserRole ur : userRoles) {
            List<String> roleKeys = userService.getUserRoleKeys(ur.getUserId());
            if (roleKeys.contains("GROUP_ADMIN")) {
                groupAdminRoleIds.add(ur.getUserId());
            }
        }

        return groupMembers.stream()
                .filter(u -> groupAdminRoleIds.contains(u.getId()))
                .collect(Collectors.toList());
    }

    public Page<AlertVO> pageAlerts(int page, int size, String alertType, Integer status, Long currentUserId, List<String> currentRoles) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        if (alertType != null && !alertType.isBlank()) {
            wrapper.eq(AlertRecord::getAlertType, alertType);
        }
        if (status != null) {
            wrapper.eq(AlertRecord::getStatus, status);
        }

        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = currentRoles.contains("GROUP_ADMIN");

        if (!isSuperAdmin) {
            User currentUser = userService.getById(currentUserId);
            if (currentUser == null) {
                return new Page<>(page, size, 0);
            }

            if (isGroupAdmin && currentUser.getGroupId() != null) {
                List<User> groupMembers = userService.list(
                        new LambdaQueryWrapper<User>().eq(User::getGroupId, currentUser.getGroupId())
                );
                List<Long> groupHostIds = new ArrayList<>();
                for (User member : groupMembers) {
                    HostInfo h = hostInfoService.getOne(
                            new LambdaQueryWrapper<HostInfo>().eq(HostInfo::getUserId, member.getId())
                    );
                    if (h != null) groupHostIds.add(h.getId());
                }
                if (groupHostIds.isEmpty()) {
                    return new Page<>(page, size, 0);
                }
                wrapper.in(AlertRecord::getHostId, groupHostIds);
            } else {
                HostInfo host = hostInfoService.getOne(
                        new LambdaQueryWrapper<HostInfo>().eq(HostInfo::getUserId, currentUserId)
                );
                if (host == null) {
                    return new Page<>(page, size, 0);
                }
                wrapper.eq(AlertRecord::getHostId, host.getId());
            }
        }

        wrapper.orderByDesc(AlertRecord::getCreateTime);
        Page<AlertRecord> recordPage = page(new Page<>(page, size), wrapper);

        Page<AlertVO> voPage = new Page<>(recordPage.getCurrent(), recordPage.getSize(), recordPage.getTotal());
        voPage.setRecords(recordPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Transactional
    public void resolveAlert(Long alertId, Long currentUserId, List<String> currentRoles) {
        AlertRecord record = getById(alertId);
        if (record == null) {
            throw new NotFoundException("告警记录不存在");
        }

        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = currentRoles.contains("GROUP_ADMIN");

        if (!isSuperAdmin) {
            User currentUser = userService.getById(currentUserId);
            if (currentUser == null) {
                throw new ForbiddenException("无权操作");
            }
            HostInfo host = hostInfoService.getById(record.getHostId());
            if (host == null) {
                throw new ForbiddenException("无权操作");
            }
            if (isGroupAdmin) {
                if (!currentUser.getGroupId().equals(hostUser(host).getGroupId())) {
                    throw new ForbiddenException("无权操作非本组告警");
                }
            } else {
                if (!host.getUserId().equals(currentUserId)) {
                    throw new ForbiddenException("无权操作非本人告警");
                }
            }
        }

        record.setStatus(1);
        updateById(record);
    }

    private User hostUser(HostInfo host) {
        return userService.getById(host.getUserId());
    }

    public Long getUnresolvedCount(Long currentUserId, List<String> currentRoles) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getStatus, 0);

        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
        boolean isGroupAdmin = currentRoles.contains("GROUP_ADMIN");

        if (!isSuperAdmin) {
            User currentUser = userService.getById(currentUserId);
            if (currentUser == null) return 0L;

            if (isGroupAdmin && currentUser.getGroupId() != null) {
                List<User> groupMembers = userService.list(
                        new LambdaQueryWrapper<User>().eq(User::getGroupId, currentUser.getGroupId())
                );
                List<Long> groupHostIds = new ArrayList<>();
                for (User member : groupMembers) {
                    HostInfo h = hostInfoService.getOne(
                            new LambdaQueryWrapper<HostInfo>().eq(HostInfo::getUserId, member.getId())
                    );
                    if (h != null) groupHostIds.add(h.getId());
                }
                if (groupHostIds.isEmpty()) return 0L;
                wrapper.in(AlertRecord::getHostId, groupHostIds);
            } else {
                HostInfo host = hostInfoService.getOne(
                        new LambdaQueryWrapper<HostInfo>().eq(HostInfo::getUserId, currentUserId)
                );
                if (host == null) return 0L;
                wrapper.eq(AlertRecord::getHostId, host.getId());
            }
        }

        return count(wrapper);
    }

    private AlertVO toVO(AlertRecord record) {
        AlertVO vo = new AlertVO();
        vo.setId(record.getId());
        vo.setHostId(record.getHostId());
        vo.setAlertType(record.getAlertType());
        vo.setProcessName(record.getProcessName());
        vo.setRuleId(record.getRuleId());
        vo.setNotificationId(record.getNotificationId());
        vo.setStatus(record.getStatus());
        vo.setCreateTime(record.getCreateTime());

        if ("BLACKLIST_RUNNING".equals(record.getAlertType())) {
            vo.setAlertTypeLabel("黑名单进程运行");
        } else if ("WHITELIST_MISSING".equals(record.getAlertType())) {
            vo.setAlertTypeLabel("白名单进程缺失");
        } else if ("CPU_HIGH".equals(record.getAlertType())) {
            vo.setAlertTypeLabel("CPU使用率过高");
        } else if ("MEMORY_HIGH".equals(record.getAlertType())) {
            vo.setAlertTypeLabel("内存使用率过高");
        } else if ("DISK_HIGH".equals(record.getAlertType())) {
            vo.setAlertTypeLabel("磁盘使用率过高");
        } else {
            vo.setAlertTypeLabel(record.getAlertType());
        }

        vo.setStatusLabel(record.getStatus() == 0 ? "未处理" : "已处理");

        HostInfo host = hostInfoService.getById(record.getHostId());
        if (host != null) {
            vo.setHostname(host.getHostname());
        }

        return vo;
    }
}
