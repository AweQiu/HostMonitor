package com.ylh.client;

import com.ylh.client.models.HostInfo;
import com.ylh.client.models.HostMetric;
import com.ylh.client.utils.CollectionUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonitorClientTest {

    @Test
    void collectHostInfoReturnsNonNullValues() {
        HostInfo info = CollectionUtil.collectHostInfo();

        assertNotNull(info);
        assertNotNull(info.getHostname());
        assertFalse(info.getHostname().isEmpty());
        assertNotNull(info.getIpAddress());
        assertNotNull(info.getCpuModel());
        assertFalse(info.getCpuModel().isEmpty());
        assertTrue(info.getCpuCores() > 0);
        assertTrue(info.getMemoryTotal() > 0);
        assertTrue(info.getDiskTotal() > 0);
        assertNotNull(info.getOsName());
        assertNotNull(info.getOsVersion());
    }

    @Test
    void collectMetricReturnsValidRanges() throws InterruptedException {
        Thread.sleep(1000);

        HostMetric metric = CollectionUtil.collectMetric();

        assertNotNull(metric);
        assertTrue(metric.getCpuUsage() >= 0 && metric.getCpuUsage() <= 100,
                "CPU usage should be between 0 and 100, got: " + metric.getCpuUsage());
        assertTrue(metric.getMemoryUsage() >= 0 && metric.getMemoryUsage() <= 100,
                "Memory usage should be between 0 and 100, got: " + metric.getMemoryUsage());
        assertTrue(metric.getDiskUsage() >= 0 && metric.getDiskUsage() <= 100,
                "Disk usage should be between 0 and 100, got: " + metric.getDiskUsage());
        assertTrue(metric.getMemoryUsed() >= 0);
        assertTrue(metric.getDiskUsed() >= 0);
        assertNotNull(metric.getCollectTime());
    }

    @Test
    void hostInfoMemoryAndDiskConsistent() {
        HostInfo info = CollectionUtil.collectHostInfo();

        assertTrue(info.getMemoryTotal() > 0);
        assertTrue(info.getDiskTotal() > 0);
    }

    @Test
    void metricMemoryAndDiskConsistent() throws InterruptedException {
        Thread.sleep(1000);

        HostMetric metric = CollectionUtil.collectMetric();
        HostInfo info = CollectionUtil.collectHostInfo();

        assertTrue(metric.getMemoryUsed() <= info.getMemoryTotal(),
                "Used memory should not exceed total memory");
        assertTrue(metric.getDiskUsed() <= info.getDiskTotal(),
                "Used disk should not exceed total disk");
    }
}
