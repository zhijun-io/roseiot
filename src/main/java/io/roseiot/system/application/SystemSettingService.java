package io.roseiot.system.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.system.persistence.SystemSetting;
import io.roseiot.system.persistence.SystemSettingMapper;
import io.roseiot.system.persistence.SystemSettingType;
import io.roseiot.shared.util.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class SystemSettingService {

    private final SystemSettingMapper settings;

    public SystemSettingService(SystemSettingMapper settings) {
        this.settings = settings;
    }

    public Optional<SystemSetting> find(SystemSettingType type) {
        return Optional.ofNullable(settings.selectByType(type));
    }

    public String requireGeneralBaseUrl() {
        Object baseUrl = find(SystemSettingType.GENERAL)
                .map(SystemSetting::getSetting)
                .map(setting -> setting.get("baseUrl"))
                .orElse(null);
        if (!(baseUrl instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("GENERAL setting baseUrl is required");
        }
        return value;
    }

    @Transactional
    public void replace(SystemSettingType type, Map<String, Object> setting) {
        if (type == null || setting == null) throw new IllegalArgumentException("type and setting are required");
        Instant now = Instant.now();
        SystemSetting current = settings.selectByType(type);
        if (current == null) {
            SystemSetting created = new SystemSetting();
            created.setId(UuidV7.generate());
            created.setType(type);
            created.setSetting(Map.copyOf(setting));
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            settings.insert(created);
        } else {
            current.setSetting(Map.copyOf(setting));
            current.setUpdatedAt(now);
            settings.updateById(current);
        }
    }
}
