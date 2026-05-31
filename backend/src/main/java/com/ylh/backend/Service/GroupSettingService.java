package com.ylh.backend.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ylh.backend.Entity.GroupSetting;
import com.ylh.backend.Mapper.GroupSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupSettingService extends ServiceImpl<GroupSettingMapper, GroupSetting> {

    private final AlertSettingService alertSettingService;

    private static final List<String> OVERRIDABLE_KEYS = List.of(
            "performance_alert_enabled",
            "cpu_threshold",
            "memory_threshold",
            "disk_threshold",
            "alert_dedup_minutes",
            "process_alert_enabled",
            "client_metric_interval_ms",
            "client_process_interval_ms",
            "client_screen_fps",
            "client_screen_jpeg_quality",
            "client_process_cpu_threshold"
    );

    public Map<String, String> getGroupSettings(Long groupId) {
        Map<String, String> result = new LinkedHashMap<>();
        List<GroupSetting> groupSettings = list(
                new LambdaQueryWrapper<GroupSetting>().eq(GroupSetting::getGroupId, groupId)
        );
        for (GroupSetting gs : groupSettings) {
            result.put(gs.getSettingKey(), gs.getSettingValue());
        }
        return result;
    }

    public Map<String, String> getEffectiveSettings(Long groupId) {
        Map<String, String> global = alertSettingService.getAllSettings();
        if (groupId == null) return global;
        Map<String, String> result = new LinkedHashMap<>(global);
        List<GroupSetting> groupSettings = list(
                new LambdaQueryWrapper<GroupSetting>().eq(GroupSetting::getGroupId, groupId)
        );
        for (GroupSetting gs : groupSettings) {
            if (OVERRIDABLE_KEYS.contains(gs.getSettingKey())) {
                result.put(gs.getSettingKey(), gs.getSettingValue());
            }
        }
        return result;
    }

    public String getEffective(Long groupId, String key, String defaultValue) {
        if (groupId != null) {
            GroupSetting gs = getOne(
                    new LambdaQueryWrapper<GroupSetting>()
                            .eq(GroupSetting::getGroupId, groupId)
                            .eq(GroupSetting::getSettingKey, key)
            );
            if (gs != null) return gs.getSettingValue();
        }
        String globalValue = alertSettingService.get(key);
        return globalValue != null ? globalValue : defaultValue;
    }

    public double getEffectiveDouble(Long groupId, String key, double defaultValue) {
        String val = getEffective(groupId, key, null);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getEffectiveLong(Long groupId, String key, long defaultValue) {
        String val = getEffective(groupId, key, null);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void updateGroupSettings(Long groupId, Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (!OVERRIDABLE_KEYS.contains(entry.getKey())) continue;
            GroupSetting existing = getOne(
                    new LambdaQueryWrapper<GroupSetting>()
                            .eq(GroupSetting::getGroupId, groupId)
                            .eq(GroupSetting::getSettingKey, entry.getKey())
            );
            if (existing != null) {
                existing.setSettingValue(entry.getValue());
                updateById(existing);
            } else {
                GroupSetting gs = new GroupSetting();
                gs.setGroupId(groupId);
                gs.setSettingKey(entry.getKey());
                gs.setSettingValue(entry.getValue());
                save(gs);
            }
        }
        log.info("Updated group {} settings: {}", groupId, settings.keySet());
    }

    public void deleteGroupSettings(Long groupId) {
        remove(new LambdaQueryWrapper<GroupSetting>().eq(GroupSetting::getGroupId, groupId));
    }

    public List<String> getOverridableKeys() {
        return OVERRIDABLE_KEYS;
    }
}
