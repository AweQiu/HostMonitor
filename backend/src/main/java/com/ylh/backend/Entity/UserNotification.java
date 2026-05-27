package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notification")
public class UserNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long notificationId;
    private Integer isRead;
    private LocalDateTime readTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
