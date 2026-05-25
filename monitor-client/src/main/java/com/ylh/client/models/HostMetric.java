package com.ylh.client.models;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class HostMetric {

    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private long memoryUsed;
    private long diskUsed;
    private LocalDateTime collectTime;

    public HostMetric() {}

    public HostMetric(double cpuUsage, double memoryUsage, double diskUsage,
                      long memoryUsed, long diskUsed, LocalDateTime collectTime) {
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.diskUsage = diskUsage;
        this.memoryUsed = memoryUsed;
        this.diskUsed = diskUsed;
        this.collectTime = collectTime;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public double getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(double diskUsage) {
        this.diskUsage = diskUsage;
    }

    public long getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(long memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public long getDiskUsed() {
        return diskUsed;
    }

    public void setDiskUsed(long diskUsed) {
        this.diskUsed = diskUsed;
    }

    public LocalDateTime getCollectTime() {
        return collectTime;
    }

    public void setCollectTime(LocalDateTime collectTime) {
        this.collectTime = collectTime;
    }

    @Override
    public String toString() {
        return "HostMetric{" +
                "cpuUsage=" + String.format("%.2f", cpuUsage) + '%' +
                ", memoryUsage=" + String.format("%.2f", memoryUsage) + '%' +
                ", diskUsage=" + String.format("%.2f", diskUsage) + '%' +
                ", memoryUsed=" + memoryUsed +
                ", diskUsed=" + diskUsed +
                ", collectTime=" + collectTime +
                '}';
    }
}
