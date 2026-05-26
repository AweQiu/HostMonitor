package com.ylh.backend.Socket;

import com.ylh.backend.Entity.HostInfo;
import com.ylh.backend.Entity.HostMetric;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageParser {

    private static final Pattern STRING_FIELD = Pattern.compile("(\\w+)='([^']*)'");
    private static final Pattern NUMERIC_FIELD = Pattern.compile("(\\w+)=([\\d.]+)%?");
    private static final Pattern DATETIME_FIELD = Pattern.compile("collectTime=([\\dT:.\\-]+)");

    private MessageParser() {
    }

    public static HostInfo parseHostInfo(String message) {
        Map<String, String> stringFields = extractFields(message, STRING_FIELD);
        Map<String, String> numericFields = extractFields(message, NUMERIC_FIELD);

        HostInfo host = new HostInfo();
        host.setHostname(stringFields.get("hostname"));
        host.setIpAddress(stringFields.get("ipAddress"));
        host.setCpuModel(stringFields.get("cpuModel"));
        host.setOsName(stringFields.get("osName"));
        host.setOsVersion(stringFields.get("osVersion"));
        host.setCpuCores(parseInt(numericFields.get("cpuCores")));
        host.setMemoryTotal(parseLong(numericFields.get("memoryTotal")));
        host.setDiskTotal(parseLong(numericFields.get("diskTotal")));
        return host;
    }

    public static HostMetric parseHostMetric(String message) {
        Map<String, String> numericFields = extractFields(message, NUMERIC_FIELD);
        Matcher dtMatcher = DATETIME_FIELD.matcher(message);

        HostMetric metric = new HostMetric();
        metric.setCpuUsage(parseBigDecimal(numericFields.get("cpuUsage")));
        metric.setMemoryUsage(parseBigDecimal(numericFields.get("memoryUsage")));
        metric.setDiskUsage(parseBigDecimal(numericFields.get("diskUsage")));
        metric.setMemoryUsed(parseLong(numericFields.get("memoryUsed")));
        metric.setDiskUsed(parseLong(numericFields.get("diskUsed")));
        if (dtMatcher.find()) {
            metric.setCollectTime(LocalDateTime.parse(dtMatcher.group(1)));
        }
        return metric;
    }

    public static boolean isHostInfoMessage(String message) {
        return message != null && message.startsWith("HostInfo{");
    }

    public static boolean isHostMetricMessage(String message) {
        return message != null && message.startsWith("HostMetric{");
    }

    private static Map<String, String> extractFields(String message, Pattern pattern) {
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            fields.put(matcher.group(1), matcher.group(2));
        }
        return fields;
    }

    private static int parseInt(String value) {
        if (value == null) return 0;
        return (int) Double.parseDouble(value);
    }

    private static long parseLong(String value) {
        if (value == null) return 0L;
        return (long) Double.parseDouble(value);
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null) return BigDecimal.ZERO;
        return new BigDecimal(value);
    }
}
