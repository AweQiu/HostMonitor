package com.ylh.backend.Socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.HostMetric;

import java.math.BigDecimal;

public final class MessageParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MessageParser() {
    }

    public static HostInfo parseHostInfo(String jsonBody) {
        try {
            HostInfoDto dto = OBJECT_MAPPER.readValue(jsonBody, HostInfoDto.class);
            HostInfo host = new HostInfo();
            host.setHostname(dto.hostname);
            host.setIpAddress(dto.ipAddress);
            host.setCpuModel(dto.cpuModel);
            host.setCpuCores(dto.cpuCores != null ? dto.cpuCores : 0);
            host.setMemoryTotal(dto.memoryTotal != null ? dto.memoryTotal : 0L);
            host.setDiskTotal(dto.diskTotal != null ? dto.diskTotal : 0L);
            host.setOsName(dto.osName);
            host.setOsVersion(dto.osVersion);
            return host;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse HostInfo: " + jsonBody, e);
        }
    }

    public static HostMetric parseHostMetric(String jsonBody) {
        try {
            HostMetricDto dto = OBJECT_MAPPER.readValue(jsonBody, HostMetricDto.class);
            HostMetric metric = new HostMetric();
            metric.setCpuUsage(dto.cpuUsage != null ? BigDecimal.valueOf(dto.cpuUsage) : BigDecimal.ZERO);
            metric.setMemoryUsage(dto.memoryUsage != null ? BigDecimal.valueOf(dto.memoryUsage) : BigDecimal.ZERO);
            metric.setDiskUsage(dto.diskUsage != null ? BigDecimal.valueOf(dto.diskUsage) : BigDecimal.ZERO);
            metric.setMemoryUsed(dto.memoryUsed != null ? dto.memoryUsed : 0L);
            metric.setDiskUsed(dto.diskUsed != null ? dto.diskUsed : 0L);
            metric.setCollectTime(dto.collectTime);
            return metric;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse HostMetric: " + jsonBody, e);
        }
    }

    private static class HostInfoDto {
        public String hostname;
        public String ipAddress;
        public String cpuModel;
        public Integer cpuCores;
        public Long memoryTotal;
        public Long diskTotal;
        public String osName;
        public String osVersion;
    }

    private static class HostMetricDto {
        public Double cpuUsage;
        public Double memoryUsage;
        public Double diskUsage;
        public Long memoryUsed;
        public Long diskUsed;
        public java.time.LocalDateTime collectTime;
    }
}
