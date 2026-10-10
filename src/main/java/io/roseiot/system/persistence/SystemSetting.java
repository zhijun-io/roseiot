package io.roseiot.system.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@TableName(value = "system_setting", autoResultMap = true)
public class SystemSetting {

    @TableId
    private UUID id;

    private SystemSettingType type;

    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> setting;

    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SystemSettingType getType() { return type; }
    public void setType(SystemSettingType type) { this.type = type; }
    public Map<String, Object> getSetting() { return setting; }
    public void setSetting(Map<String, Object> setting) { this.setting = setting; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
