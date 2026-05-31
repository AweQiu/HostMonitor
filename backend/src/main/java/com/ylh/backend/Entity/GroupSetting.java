package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("group_setting")
public class GroupSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private String settingKey;

    private String settingValue;

    private String description;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
