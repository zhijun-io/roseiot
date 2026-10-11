package io.roseiot.system;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Optional;

@Service
public class SystemSettingService {

    private final SystemSettingMapper systemSettingMapper;

    public SystemSettingService(SystemSettingMapper systemSettingMapper) {
        this.systemSettingMapper = systemSettingMapper;
    }

    Optional<SystemSetting> find(SystemSettingType type) {
        return Optional.ofNullable(systemSettingMapper.selectByType(type))
                .map(SystemSetting::from);
    }

    public String requireGeneralBaseUrl() {
        JsonNode generalSetting = find(SystemSettingType.GENERAL)
                .map(SystemSetting::setting)
                .orElse(null);
        JsonNode baseUrl = generalSetting == null ? null : generalSetting.get("baseUrl");
        if (baseUrl == null || !baseUrl.isTextual() || baseUrl.textValue().isBlank()) {
            throw new IllegalStateException("GENERAL setting baseUrl is required");
        }
        return baseUrl.textValue();
    }

    @Transactional
    SystemSetting create(SystemSettingType type, JsonNode setting) {
        if (type == null || setting == null || !setting.isObject()) {
            throw new IllegalArgumentException("type and object setting are required");
        }
        if (systemSettingMapper.selectByType(type) != null) {
            throw new IllegalStateException("System setting already exists");
        }
        Instant now = Instant.now();
        SystemSettingEntity created = new SystemSettingEntity();
        created.setType(type);
        created.setSetting(setting.deepCopy());
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        systemSettingMapper.insert(created);
        return SystemSetting.from(created);
    }

    @Transactional
    SystemSetting update(Long id, JsonNode setting) {
        if (id == null || setting == null || !setting.isObject()) {
            throw new IllegalArgumentException("id and object setting are required");
        }
        SystemSettingEntity current = systemSettingMapper.selectById(id);
        if (current == null) throw new IllegalArgumentException("System setting not found");
        current.setSetting(setting.deepCopy());
        current.setUpdatedAt(Instant.now());
        systemSettingMapper.updateById(current);
        return SystemSetting.from(current);
    }
}
