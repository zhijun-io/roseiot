package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.iam.application.UserSessionService;
import io.roseiot.iam.domain.LoginEmails;
import io.roseiot.iam.domain.PasswordPolicy;
import io.roseiot.iam.domain.TokenDigests;
import io.roseiot.iam.domain.UserStatus;
import io.roseiot.iam.persistence.RoleMapper;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.iam.persistence.UserCredential;
import io.roseiot.iam.persistence.UserCredentialMapper;
import io.roseiot.iam.persistence.UserRoleMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.tenant.persistence.TenantInvitation;
import io.roseiot.tenant.persistence.TenantInvitationMapper;
import io.roseiot.shared.util.UuidV7;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TenantInvitationService {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final TenantMapper tenantMapper;
    private final io.roseiot.iam.application.RoleService roles;
    private final TenantInvitationMapper invitationMapper;
    private final UserMapper userMapper;
    private final UserCredentialMapper userCredentialMapper;
    private final TenantInvitationNotifier invitationNotifier;
    private final UserSessionService userSessionService;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleMapper userRoleMapper;

    public TenantInvitationService(
            TenantMapper tenantMapper,
            TenantInvitationMapper invitationMapper,
            UserMapper userMapper,
            UserCredentialMapper userCredentialMapper,
            TenantInvitationNotifier invitationNotifier,
            UserSessionService userSessionService,
            @Qualifier("identityPasswordEncoder") PasswordEncoder passwordEncoder,
            UserRoleMapper userRoleMapper, io.roseiot.iam.application.RoleService roles) {
        this.tenantMapper = tenantMapper;
        this.roles = roles;
        this.invitationMapper = invitationMapper;
        this.userMapper = userMapper;
        this.userCredentialMapper = userCredentialMapper;
        this.invitationNotifier = invitationNotifier;
        this.userSessionService = userSessionService;
        this.passwordEncoder = passwordEncoder;
        this.userRoleMapper = userRoleMapper;
    }

    @Transactional
    public InvitationSummary create(UUID tenantId, String rawEmail, UUID roleId) {
        tenantMapper.requireActiveForUpdate(tenantId);
        roles.requireAssignable(tenantId, roleId);
        String email = LoginEmails.normalize(rawEmail);
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException(ApiErrorCode.INVITATION_EMAIL_CONFLICT);
        }
        try {
            return issueInvitation(tenantId, email, roleId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApiErrorCode.INVITATION_EMAIL_CONFLICT);
        }
    }

    @Transactional
    public InvitationSummary issueInvitation(UUID tenantId, String normalizedEmail, UUID roleId) {
        String plainToken = TokenDigests.generateUrlSafe256Bit();
        Instant now = Instant.now();
        TenantInvitation invitation = new TenantInvitation();
        invitation.setId(UuidV7.generate());
        invitation.setTenantId(tenantId);
        invitation.setEmail(normalizedEmail);
        invitation.setRoleId(roleId);
        invitation.setTokenHash(TokenDigests.sha256Hex(plainToken));
        invitation.setExpiresAt(now.plus(INVITATION_TTL));
        invitation.setCreatedAt(now);
        invitationMapper.insert(invitation);
        invitationNotifier.sendInvitation(normalizedEmail, roles.requireAssignable(tenantId, roleId).getName(), plainToken);
        return toSummary(invitation);
    }

    public List<InvitationSummary> list(UUID tenantId, String statusFilter) {
        tenantMapper.requireActive(tenantId);
        Instant now = Instant.now();
        LambdaQueryWrapper<TenantInvitation> query = new LambdaQueryWrapper<TenantInvitation>()
                .eq(TenantInvitation::getTenantId, tenantId)
                .orderByDesc(TenantInvitation::getCreatedAt);
        applyStatusFilter(query, statusFilter, now);
        return invitationMapper.selectList(query).stream().map(this::toSummary).toList();
    }

    public InvitationSummary get(UUID tenantId, UUID invitationId) {
        tenantMapper.requireActive(tenantId);
        TenantInvitation invitation = requireInvitationInTenant(tenantId, invitationId);
        return toSummary(invitation);
    }

    @Transactional
    public void revoke(UUID tenantId, UUID invitationId) {
        tenantMapper.requireActive(tenantId);
        TenantInvitation invitation = requireInvitationInTenant(tenantId, invitationId);
        if (invitation.getAcceptedAt() != null || invitation.getRevokedAt() != null) {
            throw new BusinessException(ApiErrorCode.INVITATION_NOT_FOUND);
        }
        invitation.setRevokedAt(Instant.now());
        invitationMapper.updateById(invitation);
    }

    public InvitationTokenState checkInvitationToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return InvitationTokenState.INVALID;
        }
        String tokenHash = TokenDigests.sha256Hex(rawToken);
        TenantInvitation row = invitationMapper.selectByToken(tokenHash);
        if (row == null || row.getAcceptedAt() != null || row.getRevokedAt() != null) {
            return InvitationTokenState.INVALID;
        }
        if (!row.getExpiresAt().isAfter(Instant.now())) {
            return InvitationTokenState.EXPIRED;
        }
        Tenant tenant = tenantMapper.selectById(row.getTenantId());
        if (tenant == null || tenant.getDisabledAt() != null) {
            return InvitationTokenState.INVALID;
        }
        return InvitationTokenState.VALID;
    }

    @Transactional
    public UserSessionService.SessionTokens acceptInvitation(String rawToken, String rawPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ApiErrorCode.INVALID_INVITATION_TOKEN);
        }
        PasswordPolicy.ensureValid(rawPassword, ApiErrorCode.VALIDATION_ERROR);

        String tokenHash = TokenDigests.sha256Hex(rawToken);
        Instant now = Instant.now();

        TenantInvitation found = invitationMapper.selectByToken(tokenHash);
        if (found == null) throw new BusinessException(ApiErrorCode.INVALID_INVITATION_TOKEN);
        return io.roseiot.tenant.infra.mybatis.TenantContext.call(found.getTenantId(), () -> {
        tenantMapper.requireActiveForUpdate(found.getTenantId());
        TenantInvitation invitation = invitationMapper.selectById(found.getId());
        if (invitation == null || invitation.getAcceptedAt() != null || invitation.getRevokedAt() != null
                || !invitation.getExpiresAt().isAfter(now))
            throw new BusinessException(ApiErrorCode.INVALID_INVITATION_TOKEN);
        roles.requireAssignable(invitation.getTenantId(), invitation.getRoleId());
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, invitation.getEmail())) > 0)
            throw new BusinessException(ApiErrorCode.INVALID_INVITATION_TOKEN);

        UUID userId = UuidV7.generate();
        User user = new User();
        user.setId(userId);
        user.setTenantId(invitation.getTenantId());
        user.setEmail(invitation.getEmail());
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthorizationVersion(1);
        user.setCreatedAt(now);
        userMapper.insert(user);

        UserCredential credential = new UserCredential();
        credential.setUserId(userId);
        credential.setPasswordHash(passwordEncoder.encode(rawPassword));
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        userCredentialMapper.insert(credential);

        userRoleMapper.insertAssignment(
                UuidV7.generate(),
                userId,
                invitation.getRoleId(),
                invitation.getTenantId());

        invitation.setAcceptedAt(now);
        invitationMapper.updateById(invitation);

        return userSessionService.openSessionForUser(userId);
        });
    }

    private TenantInvitation requireInvitationInTenant(UUID tenantId, UUID invitationId) {
        TenantInvitation invitation = invitationMapper.selectById(invitationId);
        if (invitation == null || !tenantId.equals(invitation.getTenantId())) {
            throw new BusinessException(ApiErrorCode.INVITATION_NOT_FOUND);
        }
        return invitation;
    }

    private static void applyStatusFilter(
            LambdaQueryWrapper<TenantInvitation> query, String statusFilter, Instant now) {
        String status = statusFilter == null || statusFilter.isBlank()
                ? "pending"
                : statusFilter.trim().toLowerCase(Locale.ROOT);
        switch (status) {
            case "pending" -> query.isNull(TenantInvitation::getAcceptedAt)
                    .isNull(TenantInvitation::getRevokedAt)
                    .gt(TenantInvitation::getExpiresAt, now);
            case "accepted" -> query.isNotNull(TenantInvitation::getAcceptedAt);
            case "revoked" -> query.isNotNull(TenantInvitation::getRevokedAt);
            case "expired" -> query.isNull(TenantInvitation::getAcceptedAt)
                    .isNull(TenantInvitation::getRevokedAt)
                    .le(TenantInvitation::getExpiresAt, now);
            default -> throw new IllegalArgumentException("Invalid status filter");
        }
    }

    private InvitationSummary toSummary(TenantInvitation invitation) {
        return new InvitationSummary(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRoleId(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt(),
                invitation.getAcceptedAt(),
                invitation.getRevokedAt());
    }

    public enum InvitationTokenState {
        VALID,
        EXPIRED,
        INVALID
    }

    public record InvitationSummary(
            UUID id,
            String email,
            UUID roleId,
            Instant expiresAt,
            Instant createdAt,
            Instant acceptedAt,
            Instant revokedAt) {}
}
