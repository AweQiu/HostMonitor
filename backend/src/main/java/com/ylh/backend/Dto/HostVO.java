package com.ylh.backend.Dto;

import lombok.Data;

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
}
