package io.roseiot.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@Transactional(readOnly = true)
public class SystemSettingService {

    private final SystemSettingMapper systemSettingMapper;

    public SystemSettingService(SystemSettingMapper systemSettingMapper) {
        this.systemSettingMapper = systemSettingMapper;
    }

    @PreAuthorize("hasAuthority('system.setting.manage')")
    public List<SystemSetting> list() {
        return systemSettingMapper
                .selectList(Wrappers.lambdaQuery(SystemSettingEntity.class).orderByAsc(SystemSettingEntity::getType))
                .stream()
                .map(SystemSetting::from)
                .toList();
    }

    @PreAuthorize("hasAuthority('system.setting.manage')")
    public SystemSetting get(Long id) {
        SystemSettingEntity entity = systemSettingMapper.selectById(id);
        if (entity == null) throw new BusinessException(ApiErrorCode.SYSTEM_SETTING_NOT_FOUND);
        return SystemSetting.from(entity);
    }

    public Optional<SystemSetting> find(SystemSettingType type) {
        return Optional.ofNullable(systemSettingMapper.selectByType(type)).map(SystemSetting::from);
    }

    public String requireGeneralBaseUrl() {
        JsonNode generalSetting =
                find(SystemSettingType.GENERAL).map(SystemSetting::setting).orElse(null);
        JsonNode baseUrl = generalSetting == null ? null : generalSetting.get("baseUrl");
        if (baseUrl == null || !baseUrl.isTextual() || baseUrl.textValue().isBlank()) {
            throw new IllegalStateException("GENERAL setting baseUrl is required");
        }
        return baseUrl.textValue();
    }

    @Transactional
    @PreAuthorize("hasAuthority('system.setting.manage')")
    public SystemSetting create(SystemSettingType type, JsonNode setting) {
        if (type == null || setting == null || !setting.isObject()) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "type and object setting are required");
        }
        validateSetting(type, setting);
        if (systemSettingMapper.selectByType(type) != null) {
            throw new BusinessException(ApiErrorCode.SYSTEM_SETTING_EXISTS);
        }
        SystemSettingEntity created = new SystemSettingEntity();
        created.setType(type);
        created.setSetting(setting.deepCopy());
        try {
            systemSettingMapper.insert(created);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApiErrorCode.SYSTEM_SETTING_EXISTS, null, exception);
        }
        return SystemSetting.from(created);
    }

    @Transactional
    @PreAuthorize("hasAuthority('system.setting.manage')")
    public SystemSetting update(Long id, JsonNode setting) {
        if (id == null || setting == null || !setting.isObject()) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "id and object setting are required");
        }
        SystemSettingEntity current = systemSettingMapper.selectById(id);
        if (current == null) throw new BusinessException(ApiErrorCode.SYSTEM_SETTING_NOT_FOUND);
        validateSetting(current.getType(), setting);
        current.setSetting(setting.deepCopy());
        systemSettingMapper.updateById(current);
        return SystemSetting.from(current);
    }

    @Transactional
    @PreAuthorize("hasAuthority('system.setting.manage')")
    public void delete(Long id) {
        if (systemSettingMapper.deleteById(id) == 0) {
            throw new BusinessException(ApiErrorCode.SYSTEM_SETTING_NOT_FOUND);
        }
    }

    private static void validateSetting(SystemSettingType type, JsonNode setting) {
        if (type != SystemSettingType.GENERAL) return;
        JsonNode baseUrl = setting.get("baseUrl");
        if (baseUrl == null || !baseUrl.isTextual() || baseUrl.textValue().isBlank()) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "GENERAL setting requires baseUrl");
        }
        try {
            URI uri = URI.create(baseUrl.textValue());
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ApiErrorCode.VALIDATION_ERROR, "GENERAL setting baseUrl must be an absolute HTTP(S) URL");
        }
    }
}
