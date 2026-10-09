package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.shared.util.UuidV7;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class TenantService {

    private final TenantMapper tenantMapper;
    private final TenantInvitationService invitationService;

    public TenantService(
            TenantMapper tenantMapper,
            TenantInvitationService invitationService) {
        this.tenantMapper = tenantMapper;
        this.invitationService = invitationService;
    }

    @Transactional
    public Tenant create(String name, String slug, String rawInitialAdminEmail) {
        String email = LoginEmails.normalize(rawInitialAdminEmail);
        if (tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getSlug, slug)) > 0) {
            throw new BusinessException(ApiErrorCode.TENANT_SLUG_CONFLICT);
        }

        Instant now = Instant.now();
        Tenant tenant = new Tenant();
        tenant.setId(UuidV7.generate());
        tenant.setSlug(slug);
        tenant.setName(name.trim());
        tenant.setCreatedAt(now);

        try {
            tenantMapper.insert(tenant);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApiErrorCode.TENANT_SLUG_CONFLICT);
        }
        invitationService.issueInvitation(tenant.getId(), email, TenantRoles.TENANT_ADMIN);
        return tenant;
    }

    @Transactional
    public Tenant updateTenant(UUID tenantId, String name, Boolean disabled) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ApiErrorCode.TENANT_NOT_FOUND);
        }
        Instant disabledAt = tenant.getDisabledAt();
        if (disabled != null) {
            disabledAt = disabled ? (disabledAt == null ? Instant.now() : disabledAt) : null;
        }
        if (name != null && !name.isBlank()) {
            tenant.setName(name.trim());
        }
        tenant.setDisabledAt(disabledAt);
        tenantMapper.update(null, new LambdaUpdateWrapper<Tenant>()
                .eq(Tenant::getId, tenantId)
                .set(Tenant::getName, tenant.getName())
                .set(Tenant::getDisabledAt, disabledAt));
        return tenant;
    }
}
