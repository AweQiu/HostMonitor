package com.ylh.client.utils;

import com.ylh.client.models.ProcessInfo;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 进程信息采集工具类
 * <p>
 * 基于 OSHI 库采集全量进程列表，包括 PID、父PID、进程名、用户、
 * CPU 使用率、内存使用率、内存占用、状态、启动时间和命令行。
 * </p>
 */
public final class ProcessCollector {

    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final HardwareAbstractionLayer HARDWARE = SYSTEM_INFO.getHardware();
    private static final OperatingSystem OS = SYSTEM_INFO.getOperatingSystem();

    private ProcessCollector() {
    }

    /**
     * 采集全量进程列表
     *
     * @return 进程信息列表
     */
    public static List<ProcessInfo> collectProcessList() {
        long totalMemory = HARDWARE.getMemory().getTotal();
        int cpuCores = HARDWARE.getProcessor().getLogicalProcessorCount();
        List<OSProcess> osProcesses = OS.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, 0);
        List<ProcessInfo> result = new ArrayList<>(osProcesses.size());

        for (OSProcess p : osProcesses) {
            if (p.getProcessID() == 0) {
                continue;
            }
            ProcessInfo info = new ProcessInfo();
            info.setPid(p.getProcessID());
            info.setPpid(p.getParentProcessID());
            info.setName(p.getName());
            info.setUser(p.getUser());
            double cpuLoad = p.getProcessCpuLoadBetweenTicks(p) * 100.0 / cpuCores;
            info.setCpuUsage(Math.min(cpuLoad, 100.0));
            info.setMemoryUsed(p.getResidentSetSize());
            info.setMemoryUsage(totalMemory > 0 ? (double) p.getResidentSetSize() / totalMemory * 100 : 0.0);
            info.setStatus(p.getState().name());
            info.setStartTime(toLocalDateTime(p.getStartTime()));
            info.setCommandLine(p.getCommandLine());
            result.add(info);
        }

        return result;
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        if (epochMillis <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
