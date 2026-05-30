package com.ylh.backend.Dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertVO {
    private Long id;
    private Long hostId;
    private String hostname;
    private String alertType;
    private String alertTypeLabel;
    private String processName;
    private Long ruleId;
    private Long notificationId;
    private Integer status;
    private String statusLabel;
    private LocalDateTime createTime;
}
