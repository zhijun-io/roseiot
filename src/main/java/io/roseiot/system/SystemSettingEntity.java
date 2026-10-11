package io.roseiot.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
@TableName(value = "system_setting", autoResultMap = true)
class SystemSettingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private SystemSettingType type;
    private JsonNode setting;
    private Instant createdAt;
    private Instant updatedAt;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    SystemSettingType getType() { return type; }
    void setType(SystemSettingType type) { this.type = type; }
    JsonNode getSetting() { return setting; }
    void setSetting(JsonNode setting) { this.setting = setting; }
    Instant getCreatedAt() { return createdAt; }
    void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
