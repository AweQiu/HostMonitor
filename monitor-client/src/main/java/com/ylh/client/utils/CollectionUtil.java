package com.ylh.client.utils;

import com.ylh.client.models.HostInfo;
import com.ylh.client.models.HostMetric;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 主机信息采集工具类
 * <p>
 * 基于 OSHI 库实现跨平台的主机静态信息和实时性能指标采集。
 * 提供两大核心能力：
 * <ul>
 *     <li>{@link #collectHostInfo()} - 采集主机静态信息（主机名、IP、CPU型号、内存/磁盘总量、操作系统等）</li>
 *     <li>{@link #collectMetric()} - 采集实时性能指标（CPU使用率、内存使用率、磁盘使用率等）</li>
 * </ul>
 * </p>
 *
 * <p><b>注意：</b>CPU 使用率的计算依赖两次采样之间的时间差，
 * 首次调用 {@link #collectMetric()} 前需确保类加载后至少间隔一段时间（建议 >= 1秒），
 * 否则获取到的 CPU 使用率可能为 0。</p>
 */
public final class CollectionUtil {

    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final HardwareAbstractionLayer HARDWARE = SYSTEM_INFO.getHardware();
    private static final OperatingSystem OPERATING_SYSTEM = SYSTEM_INFO.getOperatingSystem();

    /** 上一次 CPU 采样时间片，用于计算两次采样间的 CPU 使用率 */
    private static long[] prevCpuTicks = HARDWARE.getProcessor().getSystemCpuLoadTicks();

    private CollectionUtil() {
    }

    /**
     * 采集主机静态信息
     * <p>
     * 包括主机名、IP地址、CPU型号、CPU核心数、内存总量、磁盘总量、操作系统名称及版本。
     * 这些信息在主机运行期间通常不会发生变化。
     * </p>
     *
     * @return 主机静态信息对象
     */
    public static HostInfo collectHostInfo() {
        HostInfo info = new HostInfo();

        info.setHostname(getHostname());
        info.setIpAddress(getIpAddress());
        info.setCpuModel(getCpuModel());
        info.setCpuCores(getCpuCores());
        info.setMemoryTotal(getMemoryTotal());
        info.setDiskTotal(getDiskTotal());
        info.setOsName(getOsName());
        info.setOsVersion(getOsVersion());

        return info;
    }

    /**
     * 采集主机实时性能指标
     * <p>
     * 包括 CPU 使用率、内存使用率、磁盘使用率、已用内存、已用磁盘及采集时间。
     * CPU 使用率基于上次采样到本次采样之间的时间差计算。
     * </p>
     *
     * @return 主机性能指标对象
     */
    public static HostMetric collectMetric() {
        HostMetric metric = new HostMetric();

        metric.setCpuUsage(getCpuUsage());
        metric.setMemoryUsage(getMemoryUsage());
        metric.setDiskUsage(getDiskUsage());
        metric.setMemoryUsed(getMemoryUsed());
        metric.setDiskUsed(getDiskUsed());
        metric.setCollectTime(LocalDateTime.now());

        return metric;
    }

    /**
     * 获取主机名
     * <p>优先通过 {@link InetAddress} 获取，失败时回退到 OSHI 的网络参数。</p>
     */
    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return OPERATING_SYSTEM.getNetworkParams().getHostName();
        }
    }

    /**
     * 获取本机 IP 地址
     * <p>通过 {@link InetAddress} 获取本机 IP，获取失败时返回 "unknown"。</p>
     */
    private static String getIpAddress() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String hostAddress = localHost.getHostAddress();
            if (hostAddress != null && !hostAddress.isEmpty()) {
                return hostAddress;
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    /** 获取 CPU 型号名称，如 "13th Gen Intel(R) Core(TM) i5-13490F" */
    private static String getCpuModel() {
        CentralProcessor processor = HARDWARE.getProcessor();
        CentralProcessor.ProcessorIdentifier identifier = processor.getProcessorIdentifier();
        return identifier.getName().trim();
    }

    /** 获取 CPU 逻辑核心数（包含超线程） */
    private static int getCpuCores() {
        CentralProcessor processor = HARDWARE.getProcessor();
        return processor.getLogicalProcessorCount();
    }

    /** 获取物理内存总量（字节） */
    private static long getMemoryTotal() {
        GlobalMemory memory = HARDWARE.getMemory();
        return memory.getTotal();
    }

    /** 获取所有文件存储的磁盘空间总量（字节） */
    private static long getDiskTotal() {
        FileSystem fileSystem = OPERATING_SYSTEM.getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        long total = 0;
        for (OSFileStore store : fileStores) {
            total += store.getTotalSpace();
        }
        return total;
    }

    /** 获取操作系统家族名称，如 "Windows"、"Linux" */
    private static String getOsName() {
        return OPERATING_SYSTEM.getFamily();
    }

    /** 获取操作系统版本号，如 "11"、"10" */
    private static String getOsVersion() {
        return OPERATING_SYSTEM.getVersionInfo().getVersion();
    }

    /**
     * 获取 CPU 使用率（百分比）
     * <p>
     * 基于两次采样间的时间差计算。使用 synchronized 保证
     * {@code prevCpuTicks} 在多线程环境下的线程安全。
     * </p>
     *
     * @return CPU 使用率，范围 0.0 ~ 100.0
     */
    private static synchronized double getCpuUsage() {
        CentralProcessor processor = HARDWARE.getProcessor();
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = processor.getSystemCpuLoadTicks();
        if (cpuLoad < 0) {
            return 0.0;
        }
        return cpuLoad * 100;
    }

    /**
     * 获取内存使用率（百分比）
     *
     * @return 内存使用率，范围 0.0 ~ 100.0
     */
    private static double getMemoryUsage() {
        GlobalMemory memory = HARDWARE.getMemory();
        long total = memory.getTotal();
        long available = memory.getAvailable();
        if (total == 0) {
            return 0.0;
        }
        long used = total - available;
        return (double) used / total * 100;
    }

    /** 获取已用内存（字节） */
    private static long getMemoryUsed() {
        GlobalMemory memory = HARDWARE.getMemory();
        return memory.getTotal() - memory.getAvailable();
    }

    /**
     * 获取磁盘使用率（百分比）
     * <p>汇总所有文件存储分区的使用率。</p>
     *
     * @return 磁盘使用率，范围 0.0 ~ 100.0
     */
    private static double getDiskUsage() {
        FileSystem fileSystem = OPERATING_SYSTEM.getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        long total = 0;
        long used = 0;
        for (OSFileStore store : fileStores) {
            total += store.getTotalSpace();
            long free = store.getFreeSpace();
            used += (store.getTotalSpace() - free);
        }
        if (total == 0) {
            return 0.0;
        }
        return (double) used / total * 100;
    }

    /** 获取所有文件存储的已用磁盘空间（字节） */
    private static long getDiskUsed() {
        FileSystem fileSystem = OPERATING_SYSTEM.getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        long used = 0;
        for (OSFileStore store : fileStores) {
            used += (store.getTotalSpace() - store.getFreeSpace());
        }
        return used;
    }

    /**
     * 将字节数格式化为人类可读的字符串
     * <p>例如：1073741824 → "1.00 GB"</p>
     *
     * @param bytes 字节数
     * @return 格式化后的字符串，如 "1.00 GB"、"512.00 MB"
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), unit);
    }
}
