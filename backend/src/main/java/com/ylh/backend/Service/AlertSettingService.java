package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Entity.AlertSetting;
import com.ylh.backend.Mapper.AlertSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertSettingService extends ServiceImpl<AlertSettingMapper, AlertSetting> {

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 30_000;

    private void ensureCache() {
        long now = System.currentTimeMillis();
        if (now - lastCacheTime > CACHE_TTL_MS) {
            synchronized (this) {
                if (now - lastCacheTime > CACHE_TTL_MS) {
                    refreshCache();
                }
            }
        }
    }

    private void refreshCache() {
        cache.clear();
        List<AlertSetting> all = list();
        for (AlertSetting s : all) {
            cache.put(s.getSettingKey(), s.getSettingValue());
        }
        lastCacheTime = System.currentTimeMillis();
    }

    public String get(String key) {
        ensureCache();
        return cache.get(key);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val);
    }

    public int getInt(String key, int defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void set(String key, String value) {
        AlertSetting setting = getOne(
                new LambdaQueryWrapper<AlertSetting>().eq(AlertSetting::getSettingKey, key)
        );
        if (setting != null) {
            setting.setSettingValue(value);
            updateById(setting);
        } else {
            setting = new AlertSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            save(setting);
        }
        cache.put(key, value);
    }

    public Map<String, String> getAllSettings() {
        ensureCache();
        return new ConcurrentHashMap<>(cache);
    }

    public void updateSettings(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
        log.info("Updated alert settings: {}", settings.keySet());
    }
}
