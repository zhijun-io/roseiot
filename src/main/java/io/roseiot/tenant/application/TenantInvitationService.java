package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.application.UserRefreshSessions;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.domain.PasswordPolicy;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.persistence.RoleMapper;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRoleMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.tenant.persistence.TenantUserInvitation;
import io.roseiot.tenant.persistence.TenantUserInvitationMapper;
import io.roseiot.shared.util.UuidV7;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class TenantInvitationService {

    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final String PASSWORD_ALGORITHM = "bcrypt";

    private final TenantMapper tenantMapper;
    private final TenantUserInvitationMapper invitationMapper;
    private final UserMapper userMapper;
    private final TenantInvitationNotifier invitationNotifier;
    private final UserRefreshSessions refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    public TenantInvitationService(
            TenantMapper tenantMapper,
            TenantUserInvitationMapper invitationMapper,
            UserMapper userMapper,
            TenantInvitationNotifier invitationNotifier,
            UserRefreshSessions refreshSessions,
            @Qualifier("identityPasswordEncoder") PasswordEncoder passwordEncoder,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper) {
        this.tenantMapper = tenantMapper;
        this.invitationMapper = invitationMapper;
        this.userMapper = userMapper;
        this.invitationNotifier = invitationNotifier;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Transactional
    public InvitationSummary create(UUID tenantId, String rawEmail, String targetRole) {
        tenantMapper.requireActive(tenantId);
        TenantRoles.requireAssigneeRole(targetRole);
        String email = LoginEmails.normalize(rawEmail);
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException(ApiErrorCode.INVITATION_EMAIL_CONFLICT);
        }

        String plainToken = TokenDigests.generateUrlSafe256Bit();
        Instant now = Instant.now();
        TenantUserInvitation invitation = new TenantUserInvitation();
        invitation.setId(UuidV7.generate());
        invitation.setTenantId(tenantId);
        invitation.setEmail(email);
        invitation.setTargetRole(targetRole);
        invitation.setTokenHash(TokenDigests.sha256Hex(plainToken));
        invitation.setExpiresAt(now.plus(INVITATION_TTL));
        invitation.setCreatedAt(now);

        try {
            invitationMapper.insert(invitation);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApiErrorCode.INVITATION_EMAIL_CONFLICT);
        }
        invitationNotifier.sendInvitation(email, targetRole, plainToken);
        return toSummary(invitation);
    }

    public List<InvitationSummary> list(UUID tenantId, String statusFilter) {
        tenantMapper.requireActive(tenantId);
        Instant now = Instant.now();
        LambdaQueryWrapper<TenantUserInvitation> query = new LambdaQueryWrapper<TenantUserInvitation>()
                .eq(TenantUserInvitation::getTenantId, tenantId)
                .orderByDesc(TenantUserInvitation::getCreatedAt);
        applyStatusFilter(query, statusFilter, now);
        return invitationMapper.selectList(query).stream().map(this::toSummary).toList();
    }

    public InvitationSummary get(UUID tenantId, UUID invitationId) {
        tenantMapper.requireActive(tenantId);
        TenantUserInvitation invitation = requireInvitationInTenant(tenantId, invitationId);
        return toSummary(invitation);
    }

    @Transactional
    public void revoke(UUID tenantId, UUID invitationId) {
        tenantMapper.requireActive(tenantId);
        TenantUserInvitation invitation = requireInvitationInTenant(tenantId, invitationId);
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
        TenantUserInvitation row = invitationMapper.selectOne(
                new LambdaQueryWrapper<TenantUserInvitation>()
                        .eq(TenantUserInvitation::getTokenHash, tokenHash));
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

    @Transactional(noRollbackFor = BusinessException.class)
    public UserSessionService.SessionTokens acceptInvitation(String rawToken, String rawPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ApiErrorCode.INVALID_INVITATION_TOKEN);
        }
        PasswordPolicy.ensureValid(rawPassword, ApiErrorCode.VALIDATION_ERROR);

        String tokenHash = TokenDigests.sha256Hex(rawToken);
        Instant now = Instant.now();

        TenantUserInvitation invitation = invitationMapper.selectOne(
                new LambdaQueryWrapper<TenantUserInvitation>()
                        .eq(TenantUserInvitation::getTokenHash, tokenHash)
                        .isNull(TenantUserInvitation::getAcceptedAt)
                        .isNull(TenantUserInvitation::getRevokedAt)
                        .gt(TenantUserInvitation::getExpiresAt, now));

        if (invitation == null) {
            throw new BusinessException(ApiErrorCode.INVALID_INVITATION_TOKEN);
        }

        Tenant tenant = tenantMapper.selectById(invitation.getTenantId());
        if (tenant == null || tenant.getDisabledAt() != null) {
            throw new BusinessException(ApiErrorCode.INVALID_INVITATION_TOKEN);
        }

        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, invitation.getEmail())) > 0) {
            throw new BusinessException(ApiErrorCode.INVALID_INVITATION_TOKEN);
        }

        UUID userId = UuidV7.generate();
        User user = new User();
        user.setId(userId);
        user.setTenantId(invitation.getTenantId());
        user.setEmail(invitation.getEmail());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordAlgorithm(PASSWORD_ALGORITHM);
        user.setAuthorizationVersion(1);
        user.setCreatedAt(now);
        userMapper.insert(user);

        userRoleMapper.insertAssignment(
                UuidV7.generate(),
                userId,
                roleMapper.requireIdByCode(invitation.getTargetRole()),
                invitation.getTenantId());

        invitation.setAcceptedAt(now);
        invitationMapper.updateById(invitation);

        return refreshSessions.issueNewFamily(userId, now);
    }

    private TenantUserInvitation requireInvitationInTenant(UUID tenantId, UUID invitationId) {
        TenantUserInvitation invitation = invitationMapper.selectById(invitationId);
        if (invitation == null || !tenantId.equals(invitation.getTenantId())) {
            throw new BusinessException(ApiErrorCode.INVITATION_NOT_FOUND);
        }
        return invitation;
    }

    private static void applyStatusFilter(
            LambdaQueryWrapper<TenantUserInvitation> query, String statusFilter, Instant now) {
        String status = statusFilter == null || statusFilter.isBlank()
                ? "pending"
                : statusFilter.trim().toLowerCase(Locale.ROOT);
        switch (status) {
            case "pending" -> query.isNull(TenantUserInvitation::getAcceptedAt)
                    .isNull(TenantUserInvitation::getRevokedAt)
                    .gt(TenantUserInvitation::getExpiresAt, now);
            case "accepted" -> query.isNotNull(TenantUserInvitation::getAcceptedAt);
            case "revoked" -> query.isNotNull(TenantUserInvitation::getRevokedAt);
            case "expired" -> query.isNull(TenantUserInvitation::getAcceptedAt)
                    .isNull(TenantUserInvitation::getRevokedAt)
                    .le(TenantUserInvitation::getExpiresAt, now);
            default -> throw new IllegalArgumentException("Invalid status filter");
        }
    }

    private InvitationSummary toSummary(TenantUserInvitation invitation) {
        return new InvitationSummary(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getTargetRole(),
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
            String targetRole,
            Instant expiresAt,
            Instant createdAt,
            Instant acceptedAt,
            Instant revokedAt) {}
}
