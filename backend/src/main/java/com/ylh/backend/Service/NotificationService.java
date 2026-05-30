package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylh.backend.Dto.CreateNotificationRequest;
import com.ylh.backend.Dto.NotificationVO;
import com.ylh.backend.Entity.Group;
import com.ylh.backend.Entity.Notification;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Entity.UserNotification;
import com.ylh.backend.Mapper.GroupMapper;
import com.ylh.backend.Mapper.NotificationMapper;
import com.ylh.backend.Mapper.UserMapper;
import com.ylh.backend.Mapper.UserNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {

    private final UserNotificationMapper userNotificationMapper;
    private final UserMapper userMapper;
    private final GroupMapper groupMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void sendNotification(CreateNotificationRequest request, Long senderId,
                                  String senderRole, Long senderGroupId) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new RuntimeException("通知标题不能为空");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new RuntimeException("通知内容不能为空");
        }
        if (request.getSendType() == null) {
            throw new RuntimeException("请选择发送范围");
        }

        boolean isSuperAdmin = "SUPER_ADMIN".equals(senderRole);
        boolean isGroupAdmin = "GROUP_ADMIN".equals(senderRole);

        if (!isSuperAdmin && !isGroupAdmin) {
            throw new RuntimeException("无权发送通知");
        }

        List<Long> targetUserIds = new ArrayList<>();

        if (request.getSendType() == 1) {
            if (!isSuperAdmin) {
                throw new RuntimeException("只有超级管理员可以发送全体通知");
            }
            List<User> allUsers = userMapper.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getStatus, 1)
            );
            targetUserIds = allUsers.stream().map(User::getId).collect(Collectors.toList());
        } else if (request.getSendType() == 2) {
            Long groupId = request.getTargetGroupId();
            if (groupId == null) {
                throw new RuntimeException("请选择目标分组");
            }
            if (isGroupAdmin && !isSuperAdmin) {
                if (!groupId.equals(senderGroupId)) {
                    throw new RuntimeException("只能向管理的分组发送通知");
                }
            }
            List<User> groupMembers = userMapper.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getGroupId, groupId).eq(User::getStatus, 1)
            );
            targetUserIds = groupMembers.stream().map(User::getId).collect(Collectors.toList());
        } else if (request.getSendType() == 3) {
            if (request.getTargetUserIds() == null || request.getTargetUserIds().isEmpty()) {
                throw new RuntimeException("请选择目标用户");
            }
            if (isGroupAdmin && !isSuperAdmin) {
                for (Long uid : request.getTargetUserIds()) {
                    User targetUser = userMapper.selectById(uid);
                    if (targetUser == null || !senderGroupId.equals(targetUser.getGroupId())) {
                        throw new RuntimeException("只能向组内成员发送通知");
                    }
                }
            }
            targetUserIds = new ArrayList<>(request.getTargetUserIds());
        }

        if (targetUserIds.isEmpty()) {
            throw new RuntimeException("没有目标用户");
        }

        Notification notification = new Notification();
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setSenderId(senderId);
        notification.setSendType(request.getSendType());
        notification.setTargetGroupId(request.getTargetGroupId());
        notification.setStatus(1);
        notification.setSendTime(LocalDateTime.now());

        if (request.getSendType() == 3 && request.getTargetUserIds() != null) {
            try {
                notification.setTargetUserIds(objectMapper.writeValueAsString(request.getTargetUserIds()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize target user ids", e);
            }
        }

        save(notification);

        for (Long userId : targetUserIds) {
            UserNotification un = new UserNotification();
            un.setUserId(userId);
            un.setNotificationId(notification.getId());
            un.setIsRead(0);
            userNotificationMapper.insert(un);
        }

        log.info("用户 {} 发送了通知 [{}]，目标用户数: {}", senderId, notification.getTitle(), targetUserIds.size());
    }

    public Page<NotificationVO> getUserNotifications(Long userId, int page, int size) {
        Page<UserNotification> unPage = userNotificationMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .orderByDesc(UserNotification::getCreateTime)
        );

        Page<NotificationVO> voPage = new Page<>(unPage.getCurrent(), unPage.getSize(), unPage.getTotal());
        voPage.setRecords(unPage.getRecords().stream().map(un -> {
            Notification notif = getById(un.getNotificationId());
            if (notif == null) return null;
            return toVO(notif, un.getIsRead());
        }).filter(vo -> vo != null).collect(Collectors.toList()));

        return voPage;
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        UserNotification un = userNotificationMapper.selectOne(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getNotificationId, notificationId)
        );
        if (un != null && un.getIsRead() == 0) {
            un.setIsRead(1);
            un.setReadTime(LocalDateTime.now());
            userNotificationMapper.updateById(un);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<UserNotification> unreadList = userNotificationMapper.selectList(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, 0)
        );
        LocalDateTime now = LocalDateTime.now();
        for (UserNotification un : unreadList) {
            un.setIsRead(1);
            un.setReadTime(now);
            userNotificationMapper.updateById(un);
        }
    }

    public Long getUnreadCount(Long userId) {
        return userNotificationMapper.selectCount(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, 0)
        );
    }

    @Transactional
    public void deleteUserNotification(Long userId, Long notificationId) {
        userNotificationMapper.delete(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getNotificationId, notificationId)
        );
    }

    private NotificationVO toVO(Notification notif, Integer isRead) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notif.getId());
        vo.setTitle(notif.getTitle());
        vo.setContent(notif.getContent());
        vo.setSendType(notif.getSendType());
        vo.setIsRead(isRead);
        vo.setSendTime(notif.getSendTime());

        if (notif.getSenderId() != null) {
            User sender = userMapper.selectById(notif.getSenderId());
            vo.setSenderName(sender != null ? sender.getUsername() : "未知");
        } else {
            vo.setSenderName("系统");
        }

        switch (notif.getSendType()) {
            case 1:
                vo.setTargetInfo("全体用户");
                break;
            case 2:
                if (notif.getTargetGroupId() != null) {
                    Group group = groupMapper.selectById(notif.getTargetGroupId());
                    vo.setTargetInfo(group != null ? group.getGroupName() : "未知分组");
                }
                break;
            case 3:
                vo.setTargetInfo("指定用户");
                break;
        }

        return vo;
    }

    @Transactional
    public Long sendSystemNotification(String title, String content, List<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) return null;

        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setContent(content);
        notification.setSenderId(null);
        notification.setSendType(3);
        notification.setStatus(1);
        notification.setSendTime(LocalDateTime.now());

        try {
            notification.setTargetUserIds(objectMapper.writeValueAsString(targetUserIds));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize target user ids", e);
        }

        save(notification);

        for (Long userId : targetUserIds) {
            UserNotification un = new UserNotification();
            un.setUserId(userId);
            un.setNotificationId(notification.getId());
            un.setIsRead(0);
            userNotificationMapper.insert(un);
        }

        log.info("系统自动发送通知 [{}]，目标用户数: {}", title, targetUserIds.size());
        return notification.getId();
    }
}
