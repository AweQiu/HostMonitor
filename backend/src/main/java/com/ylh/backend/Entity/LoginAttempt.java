package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("login_attempt")
public class LoginAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String ipAddress;

    private Integer success;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
