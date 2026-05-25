package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("host")
public class HostInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String hostname;
    private String ipAddress;
    private String cpuModel;
    private Integer cpuCores;
    private Long memoryTotal;
    private Long diskTotal;
    private String osName;
    private String osVersion;
    private Integer status;
    private LocalDateTime lastHeartbeat;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDel;
}
