package com.ylh.backend.Dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProcessVO {
    private Long id;
    private Long hostId;
    private Integer pid;
    private Integer ppid;
    private String name;
    private String user;
    private BigDecimal cpuUsage;
    private BigDecimal memoryUsage;
    private Long memoryUsed;
    private String status;
    private LocalDateTime startTime;
    private String commandLine;
    private LocalDateTime collectTime;
    private Boolean isWhitelisted;
    private Boolean isBlacklisted;
}
