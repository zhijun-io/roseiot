package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.roseiot.iam.domain.LoginEmails;
import io.roseiot.iam.application.RoleService;
import io.roseiot.iam.persistence.RoleMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.shared.mybatis.TenantContext;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.shared.util.UuidV7;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantMapper tenantMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final TenantInvitationService invitationService;

    public TenantService(TenantMapper tenantMapper, TenantInvitationService invitationService,
                         RoleMapper roleMapper, RoleService roleService) {
        this.tenantMapper = tenantMapper;
        this.roleMapper = roleMapper;
        this.roleService = roleService;
        this.invitationService = invitationService;
    }

    @Transactional
    public Tenant create(String name, String slug, String rawInitialAdminEmail) {
        if ("default".equals(slug))
            throw new org.springframework.security.access.AccessDeniedException("Tenant creation unavailable");
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
        TenantContext.call(tenant.getId(), () -> {
            roleService.initializeBuiltInRoles(tenant.getId());
            invitationService.issueInvitation(tenant.getId(), email,
                    roleMapper.requireIdByCode(tenant.getId(), io.roseiot.iam.application.RoleService.TENANT_ADMIN));
            return null;
        });
        return tenant;
    }

    @Transactional
    public Tenant updateTenant(UUID tenantId, String name, Boolean disabled) {
        if (tenantId.equals(tenantMapper.requireDefaultTenantId()))
            throw new org.springframework.security.access.AccessDeniedException("Reserved tenant");
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
