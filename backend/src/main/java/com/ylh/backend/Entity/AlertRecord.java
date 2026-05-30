package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_record")
public class AlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hostId;
    private String alertType;
    private String processName;
    private Long ruleId;
    private Long notificationId;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
