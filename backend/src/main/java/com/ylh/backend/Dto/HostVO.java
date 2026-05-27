package com.ylh.backend.Dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HostVO {
    private Long id;
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
    private Long userId;
    private String username;
    private Long groupId;
    private String groupName;
    private LocalDateTime createTime;

    @Data
    public static class MetricVO {
        private BigDecimal cpuUsage;
        private BigDecimal memoryUsage;
        private BigDecimal diskUsage;
        private Long memoryUsed;
        private Long diskUsed;
        private LocalDateTime collectTime;
    }

    @Data
    public static class MetricHistoryVO {
        private LocalDateTime collectTime;
        private BigDecimal cpuUsage;
        private BigDecimal memoryUsage;
        private BigDecimal diskUsage;
    }
}
