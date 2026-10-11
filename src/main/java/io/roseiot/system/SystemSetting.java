package io.roseiot.system;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
/** Public system setting model; persistence representation stays within the setting feature. */
public record SystemSetting(
        Long id,
        SystemSettingType type,
        JsonNode setting,
        Instant createdAt,
        Instant updatedAt) {

    public SystemSetting {
        setting = setting == null ? null : setting.deepCopy();
    }

    @Override
    public JsonNode setting() {
        return setting == null ? null : setting.deepCopy();
    }

    static SystemSetting from(SystemSettingEntity entity) {
        return new SystemSetting(entity.getId(), entity.getType(), entity.getSetting(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
