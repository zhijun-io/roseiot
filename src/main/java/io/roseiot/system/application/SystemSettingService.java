package io.roseiot.system.application;

import io.roseiot.system.persistence.SystemSetting;
import io.roseiot.system.persistence.SystemSettingMapper;
import io.roseiot.system.persistence.SystemSettingType;
import io.roseiot.shared.util.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

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
        JsonNode generalSetting = find(SystemSettingType.GENERAL)
                .map(SystemSetting::getSetting)
                .orElse(null);
        JsonNode baseUrl = generalSetting == null ? null : generalSetting.get("baseUrl");
        if (baseUrl == null || !baseUrl.isTextual() || baseUrl.textValue().isBlank()) {
            throw new IllegalStateException("GENERAL setting baseUrl is required");
        }
        return baseUrl.textValue();
    }

    @Transactional
    public void replace(SystemSettingType type, JsonNode setting) {
        if (type == null || setting == null || !setting.isObject()) {
            throw new IllegalArgumentException("type and object setting are required");
        }
        Instant now = Instant.now();
        SystemSetting current = settings.selectByType(type);
        if (current == null) {
            SystemSetting created = new SystemSetting();
            created.setId(UuidV7.generate());
            created.setType(type);
            created.setSetting(setting.deepCopy());
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            settings.insert(created);
        } else {
            current.setSetting(setting.deepCopy());
            current.setUpdatedAt(now);
            settings.updateById(current);
        }
    }
}
