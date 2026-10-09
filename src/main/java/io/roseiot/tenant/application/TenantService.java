package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.tenant.domain.TenantNotFoundException;
import io.roseiot.tenant.domain.TenantSlugConflictException;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.tenant.persistence.TenantUserInvitation;
import io.roseiot.tenant.persistence.TenantUserInvitationMapper;
import io.roseiot.shared.util.UuidV7;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class TenantService {

    public static final String TARGET_ROLE_TENANT_ADMIN = "TenantAdmin";

    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final TenantMapper tenantMapper;
    private final TenantUserInvitationMapper invitationMapper;
    private final TenantInvitationNotifier tenantInvitationNotifier;

    public TenantService(
            TenantMapper tenantMapper,
            TenantUserInvitationMapper invitationMapper,
            TenantInvitationNotifier tenantInvitationNotifier) {
        this.tenantMapper = tenantMapper;
        this.invitationMapper = invitationMapper;
        this.tenantInvitationNotifier = tenantInvitationNotifier;
    }

    @Transactional
    public Tenant create(String name, String slug, String rawInitialAdminEmail) {
        String email = LoginEmails.normalize(rawInitialAdminEmail);
        if (tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getSlug, slug)) > 0) {
            throw new TenantSlugConflictException();
        }

        Instant now = Instant.now();
        Tenant tenant = new Tenant();
        tenant.setId(UuidV7.generate());
        tenant.setSlug(slug);
        tenant.setName(name.trim());
        tenant.setCreatedAt(now);

        String plainToken = TokenDigests.generateUrlSafe256Bit();
        TenantUserInvitation invitation = new TenantUserInvitation();
        invitation.setId(UuidV7.generate());
        invitation.setTenantId(tenant.getId());
        invitation.setEmail(email);
        invitation.setTargetRole(TARGET_ROLE_TENANT_ADMIN);
        invitation.setTokenHash(TokenDigests.sha256Hex(plainToken));
        invitation.setExpiresAt(now.plus(INVITATION_TTL));
        invitation.setCreatedAt(now);

        try {
            tenantMapper.insert(tenant);
            invitationMapper.insert(invitation);
        } catch (DuplicateKeyException exception) {
            throw new TenantSlugConflictException();
        }
        tenantInvitationNotifier.sendInvitation(email, TARGET_ROLE_TENANT_ADMIN, plainToken);
        return tenant;
    }

    @Transactional
    public Tenant updateTenant(UUID tenantId, String name, Boolean disabled) {
        Tenant current = tenantMapper.selectById(tenantId);
        if (current == null) {
            throw new TenantNotFoundException();
        }

        LambdaUpdateWrapper<Tenant> update = new LambdaUpdateWrapper<Tenant>().eq(Tenant::getId, tenantId);
        boolean changed = false;
        if (name != null && !name.isBlank()) {
            update.set(Tenant::getName, name.trim());
            changed = true;
        }
        boolean currentlyDisabled = current.getDisabledAt() != null;
        if (disabled != null && disabled != currentlyDisabled) {
            update.set(Tenant::getDisabledAt, disabled ? Instant.now() : null);
            changed = true;
        }
        if (!changed) {
            return current;
        }
        tenantMapper.update(null, update);
        return tenantMapper.selectById(tenantId);
    }
}
