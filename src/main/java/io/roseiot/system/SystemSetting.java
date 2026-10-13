package io.roseiot.system;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Public system setting model; persistence representation stays within the setting feature. */
record SystemSetting(Long id, SystemSettingType type, JsonNode setting, Instant createdAt, Instant updatedAt) {

    public SystemSetting {
        setting = setting == null ? null : setting.deepCopy();
    }

    @Override
    public JsonNode setting() {
        return setting == null ? null : setting.deepCopy();
    }

    @Override
    public String toString() {
        return "SystemSetting[id=" + id + ", type=" + type + ", setting=redacted" + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "]";
    }

    static SystemSetting from(SystemSettingEntity entity) {
        return new SystemSetting(
                entity.getId(), entity.getType(), entity.getSetting(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
