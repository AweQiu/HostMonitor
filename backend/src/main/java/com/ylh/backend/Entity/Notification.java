package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String content;
    private Long senderId;
    private Integer sendType;
    private Long targetGroupId;
    private String targetUserIds;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime sendTime;
}
