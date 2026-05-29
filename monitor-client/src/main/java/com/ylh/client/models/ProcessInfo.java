package com.ylh.client.models;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessInfo {
    private int pid;
    private int ppid;
    private String name;
    private String user;
    private double cpuUsage;
    private double memoryUsage;
    private long memoryUsed;
    private String status;
    private LocalDateTime startTime;
    private String commandLine;
}
