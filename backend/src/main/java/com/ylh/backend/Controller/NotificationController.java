package com.ylh.backend.Controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ylh.backend.Common.Result;
import com.ylh.backend.Dto.CreateNotificationRequest;
import com.ylh.backend.Dto.NotificationVO;
import com.ylh.backend.Entity.User;
import com.ylh.backend.Service.NotificationService;
import com.ylh.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @PostMapping
    public Result<Void> sendNotification(@RequestBody CreateNotificationRequest request) {
        Long currentUserId = getCurrentUserId();
        List<String> currentRoles = userService.getUserRoleKeys(currentUserId);
        User currentUser = userService.getById(currentUserId);

        String senderRole = currentRoles.contains("SUPER_ADMIN") ? "SUPER_ADMIN" :
                currentRoles.contains("GROUP_ADMIN") ? "GROUP_ADMIN" : "USER";

        notificationService.sendNotification(request, currentUserId, senderRole, currentUser.getGroupId());
        return Result.success("发送成功", null);
    }

    @GetMapping
    public Result<Page<NotificationVO>> listNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        return Result.success(notificationService.getUserNotifications(userId, page, size));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        notificationService.markAsRead(userId, id);
        return Result.success("已读", null);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return Result.success("全部已读", null);
    }

    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long userId = getCurrentUserId();
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        notificationService.deleteUserNotification(userId, id);
        return Result.success("删除成功", null);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
