package com.ylh.client.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置文件读取工具类
 * <p>
 * 提供从 classpath 下加载 Properties 配置文件的通用能力，
 * 供其他模块（如 NetworkClient）统一调用，避免配置读取逻辑散落在各处。
 * </p>
 *
 * <p><b>典型用法：</b></p>
 * <pre>
 *     Properties props = ConfigUtil.loadProperties("application.properties");
 *     String host = props.getProperty("socket.host", "127.0.0.1");
 * </pre>
 */
public final class ConfigUtil {

    private ConfigUtil() {
    }

    /**
     * 从 classpath 加载 Properties 配置文件
     *
     * @param filename 配置文件名，如 "application.properties"
     * @return 加载后的 Properties 对象
     * @throws RuntimeException 配置文件不存在或读取失败时抛出
     */
    public static Properties loadProperties(String filename) {
        Properties props = new Properties();
        try (InputStream is = ConfigUtil.class.getClassLoader().getResourceAsStream(filename)) {
            if (is == null) {
                throw new RuntimeException(filename + " not found");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + filename, e);
        }
        return props;
    }
}
