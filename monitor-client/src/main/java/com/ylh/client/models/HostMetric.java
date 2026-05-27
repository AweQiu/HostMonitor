package com.ylh.client.models;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HostMetric {
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private long memoryUsed;
    private long diskUsed;
    private LocalDateTime collectTime;
}
