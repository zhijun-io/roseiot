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

    static SystemSetting from(SystemSettingEntity entity) {
        return new SystemSetting(entity.getId(), entity.getType(), entity.getSetting(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
