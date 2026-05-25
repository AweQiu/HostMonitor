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

    public HostInfo() {}

    public HostInfo(String hostname, String ipAddress, String cpuModel, int cpuCores,
                    long memoryTotal, long diskTotal, String osName, String osVersion) {
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.cpuModel = cpuModel;
        this.cpuCores = cpuCores;
        this.memoryTotal = memoryTotal;
        this.diskTotal = diskTotal;
        this.osName = osName;
        this.osVersion = osVersion;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    public long getMemoryTotal() {
        return memoryTotal;
    }

    public void setMemoryTotal(long memoryTotal) {
        this.memoryTotal = memoryTotal;
    }

    public long getDiskTotal() {
        return diskTotal;
    }

    public void setDiskTotal(long diskTotal) {
        this.diskTotal = diskTotal;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    @Override
    public String toString() {
        return "HostInfo{" +
                "hostname='" + hostname + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", cpuModel='" + cpuModel + '\'' +
                ", cpuCores=" + cpuCores +
                ", memoryTotal=" + memoryTotal +
                ", diskTotal=" + diskTotal +
                ", osName='" + osName + '\'' +
                ", osVersion='" + osVersion + '\'' +
                '}';
    }
}
