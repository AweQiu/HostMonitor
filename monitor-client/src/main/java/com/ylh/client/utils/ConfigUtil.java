package com.ylh.client.utils;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * 配置文件读取工具类
 * <p>
 * 提供从 classpath 下加载 YAML 配置文件的通用能力，并将嵌套结构展平为
 * "a.b.c" 形式的 Properties，供其他模块（如 NetworkClient）统一调用，
 * 避免配置读取逻辑散落在各处。
 * </p>
 *
 * <p><b>典型用法：</b></p>
 * <pre>
 *     Properties props = ConfigUtil.loadProperties("application.yml");
 *     String host = props.getProperty("socket.host", "127.0.0.1");
 * </pre>
 */
public final class ConfigUtil {

    private ConfigUtil() {
    }

    /**
     * 从 classpath 加载 YAML 配置文件并展平为 Properties
     *
     * @param filename 配置文件名，如 "application.yml"
     * @return 展平后的 Properties 对象，嵌套键以 "." 连接（如 socket.host）
     * @throws RuntimeException 配置文件不存在或读取失败时抛出
     */
    public static Properties loadProperties(String filename) {
        Properties props = new Properties();
        try (InputStream is = ConfigUtil.class.getClassLoader().getResourceAsStream(filename)) {
            if (is == null) {
                throw new RuntimeException(filename + " not found");
            }
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object root = yaml.load(is);
            if (root instanceof Map<?, ?> map) {
                flatten("", map, props);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + filename, e);
        }
        return props;
    }

    /**
     * 将嵌套 Map 递归展平为 "a.b.c=value" 形式的 Properties
     */
    private static void flatten(String prefix, Map<?, ?> map, Properties props) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(key, nested, props);
            } else if (value != null) {
                props.setProperty(key, String.valueOf(value));
            }
        }
    }
}
