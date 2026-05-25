package com.ylh.client;

import com.hostmonitor.client.network.NetworkClient;
import com.ylh.client.models.HostInfo;
import com.ylh.client.models.HostMetric;
import com.ylh.client.utils.CollectionUtil;

/**
 * 监控客户端主入口
 * <p>
 * 启动后采集当前主机的静态信息和实时性能指标，并输出到控制台。
 * </p>
 */
public class MonitorClient {

    public static void main(String[] args) throws InterruptedException {
        NetworkClient.connect();
        
        HostInfo hostInfo = new HostInfo();
        HostMetric metric = new HostMetric();

        hostInfo = CollectionUtil.collectHostInfo();
        NetworkClient.send(hostInfo.getBytes());
        while (true) {
            metric = CollectionUtil.collectMetric();
            NetworkClient.send(metric.getBytes());

            Thread.sleep(1000);
        }
        NetworkClient.disconnect();
    }
}
