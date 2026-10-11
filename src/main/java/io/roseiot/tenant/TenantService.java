package io.roseiot.tenant;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.time.Instant;

@Service
public class TenantService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantMapper tenantMapper;
    public TenantService(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    public void requireCustomerTenant(Long tenantId) {
        if (Tenant.DEFAULT_TENANT_ID.equals(tenantId)) {
            throw new AccessDeniedException("Reserved tenant");
        }
    }

    public void requireActive(Long tenantId) {
        ensureActive(tenantMapper.selectById(tenantId));
    }

    public void requireActiveForUpdate(Long tenantId) {
        ensureActive(tenantMapper.selectByIdForUpdate(tenantId));
    }

    public boolean isActive(Long tenantId) {
        return tenantId != null && isActive(tenantMapper.selectById(tenantId));
    }

    @Transactional
    public Tenant create(String name) {
        Instant now = Instant.now();
        TenantEntity tenant = new TenantEntity();
        tenant.setCode(generateCode());
        tenant.setName(name.trim());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setCreatedAt(now);

        tenantMapper.insert(tenant);
        return Tenant.from(tenant);
    }

    @Transactional
    public Tenant updateTenant(Long tenantId, String name, Boolean disabled) {
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) throw new BusinessException(ApiErrorCode.TENANT_NOT_FOUND);
        requireCustomerTenant(tenantId);
        TenantStatus status = tenant.getStatus();
        if (disabled != null) {
            status = disabled ? TenantStatus.DISABLED : TenantStatus.ACTIVE;
        }
        if (name != null && !name.isBlank()) {
            tenant.setName(name.trim());
        }
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
        return Tenant.from(tenant);
    }

    private static void ensureActive(TenantEntity tenant) {
        if (!isActive(tenant)) {
            throw new BusinessException(ApiErrorCode.TENANT_NOT_FOUND);
        }
    }

    private static boolean isActive(TenantEntity tenant) {
        return tenant != null && tenant.getStatus() == TenantStatus.ACTIVE;
    }

    private static String generateCode() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
