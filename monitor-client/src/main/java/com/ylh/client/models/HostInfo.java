package com.ylh.client.models;

import lombok.Data;

@Data
public class HostInfo {
    private String hostname;
    private String ipAddress;
    private String cpuModel;
    private int cpuCores;
    private long memoryTotal;
    private long diskTotal;
    private String osName;
    private String osVersion;
}
