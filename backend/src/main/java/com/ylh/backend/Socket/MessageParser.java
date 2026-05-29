package com.ylh.backend.Socket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.HostMetric;
import com.ylh.backend.Entity.HostProcess;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class MessageParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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

    public static List<HostProcess> parseProcessList(String jsonBody) {
        try {
            List<ProcessDto> processDtos = OBJECT_MAPPER.readValue(jsonBody, new TypeReference<List<ProcessDto>>() {});
            LocalDateTime now = LocalDateTime.now();
            List<HostProcess> result = new ArrayList<>();
            for (ProcessDto p : processDtos) {
                HostProcess hp = new HostProcess();
                hp.setPid(p.pid != null ? p.pid : 0);
                hp.setPpid(p.ppid);
                hp.setName(p.name);
                hp.setUser(p.user);
                hp.setCpuUsage(p.cpuUsage != null ? BigDecimal.valueOf(p.cpuUsage) : BigDecimal.ZERO);
                hp.setMemoryUsage(p.memoryUsage != null ? BigDecimal.valueOf(p.memoryUsage) : BigDecimal.ZERO);
                hp.setMemoryUsed(p.memoryUsed);
                hp.setStatus(p.status);
                hp.setStartTime(p.startTime);
                hp.setCommandLine(p.commandLine);
                hp.setCollectTime(now);
                result.add(hp);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ProcessList: " + jsonBody, e);
        }
    }

    public static ProcessListDeltaDto parseProcessListDelta(String jsonBody) {
        try {
            return OBJECT_MAPPER.readValue(jsonBody, ProcessListDeltaDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ProcessListDelta: " + jsonBody, e);
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
        public LocalDateTime collectTime;
    }

    public static class ProcessDto {
        public Integer pid;
        public Integer ppid;
        public String name;
        public String user;
        public Double cpuUsage;
        public Double memoryUsage;
        public Long memoryUsed;
        public String status;
        public LocalDateTime startTime;
        public String commandLine;
    }

    public static class ProcessListDeltaDto {
        public Integer seq;
        public List<ProcessDto> added;
        public List<Integer> removed;
        public List<ProcessDto> changed;
    }
}
