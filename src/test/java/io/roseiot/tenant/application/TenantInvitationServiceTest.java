package io.roseiot.tenant.application;

import io.roseiot.iam.application.RoleService;
import io.roseiot.iam.domain.UserStatus;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.domain.TokenDigests;
import io.roseiot.iam.persistence.Role;
import io.roseiot.iam.persistence.RoleMapper;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserCredentialMapper;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.iam.persistence.UserCredential;
import io.roseiot.iam.persistence.UserRoleMapper;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.infra.notifier.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.tenant.persistence.TenantInvitation;
import io.roseiot.tenant.persistence.TenantInvitationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantInvitationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000a1");
    private static final UUID ROLE_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000a2");

    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private TenantInvitationMapper invitationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserCredentialMapper userCredentialMapper;
    @Mock
    private TenantInvitationNotifier invitationNotifier;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private JwtTokenFactory jwtTokenFactory;
    @Mock
    private RoleService roles;
    @InjectMocks
    private TenantInvitationService cut;

    private static Role operatorRole() {
        Role role = new Role();
        role.setId(ROLE_ID);
        role.setTenantId(TENANT_ID);
        role.setCode(RoleService.DEVICE_OPERATOR);
        role.setName("Operator");
        return role;
    }

    @Test
    void create_shouldRejectExistingUserEmail() {
        given(userMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> cut.create(TENANT_ID, "ops@roseiot.io", ROLE_ID))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(invitationMapper, invitationNotifier);
    }

    @Test
    void create_shouldPersistInvitationAndNotifyWithTheAssignedRole() {
        given(userMapper.selectCount(any())).willReturn(0L);
        when(roles.requireAssignable(TENANT_ID, ROLE_ID)).thenReturn(operatorRole());

        cut.create(TENANT_ID, "ops@roseiot.io", ROLE_ID);

        ArgumentCaptor<TenantInvitation> captor = ArgumentCaptor.forClass(TenantInvitation.class);
        verify(invitationMapper).insert(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ops@roseiot.io");
        assertThat(captor.getValue().getRoleId()).isEqualTo(ROLE_ID);
        verify(invitationNotifier).sendInvitation(eq("ops@roseiot.io"), eq("Operator"), any());
    }

    @Test
    void accept_shouldRejectUnknownToken() {
        given(invitationMapper.selectByToken(any())).willReturn(null);

        assertThatThrownBy(() -> cut.acceptInvitation("bad-token", "newSecurePass9012"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void accept_shouldCreateUserAssignRoleAndIssueAccessToken() {
        TenantInvitation stored = pendingInvitation("token");
        when(invitationMapper.selectByToken(TokenDigests.sha256Hex("token"))).thenReturn(stored);
        when(invitationMapper.selectById(stored.getId())).thenReturn(stored);
        when(roles.requireAssignable(TENANT_ID, ROLE_ID)).thenReturn(operatorRole());
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("newSecurePass9012")).thenReturn("hash");
        when(jwtTokenFactory.createAccessToken(any(), eq(TENANT_ID), any())).thenReturn("access");

        String result = cut.acceptInvitation("token", "newSecurePass9012");

        assertThat(result).isEqualTo("access");
        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getEmail()).isEqualTo(stored.getEmail());
        assertThat(inserted.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(inserted.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        ArgumentCaptor<UserCredential> credential = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialMapper).insert(credential.capture());
        assertThat(credential.getValue().getUserId()).isEqualTo(inserted.getValue().getId());
        assertThat(credential.getValue().getPassword()).isEqualTo("hash");
        assertThat(credential.getValue().getCreatedAt()).isNotNull();
        verify(userRoleMapper).insertAssignment(any(), eq(inserted.getValue().getId()), eq(ROLE_ID), eq(TENANT_ID));
        verify(invitationMapper).updateById(stored);
        assertThat(stored.getAcceptedAt()).isNotNull();
    }

    @Test
    void check_shouldMarkExpiredInvitation() {
        TenantInvitation row = pendingInvitation("token");
        row.setExpiresAt(Instant.now().minusSeconds(60));
        when(invitationMapper.selectByToken(TokenDigests.sha256Hex("token"))).thenReturn(row);

        assertThat(cut.checkInvitationToken("token"))
                .isEqualTo(TenantInvitationService.InvitationTokenState.EXPIRED);

        verifyNoInteractions(roles, userMapper, tenantMapper);
    }

    @Test
    void check_shouldMarkValidInvitation() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(tenant);
        when(invitationMapper.selectByToken(TokenDigests.sha256Hex("token")))
                .thenReturn(pendingInvitation("token"));

        assertThat(cut.checkInvitationToken("token"))
                .isEqualTo(TenantInvitationService.InvitationTokenState.VALID);
    }

    private TenantInvitation pendingInvitation(String plainToken) {
        TenantInvitation invitation = new TenantInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setTenantId(TENANT_ID);
        invitation.setEmail("member@roseiot.io");
        invitation.setRoleId(ROLE_ID);
        invitation.setTokenHash(TokenDigests.sha256Hex(plainToken));
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        return invitation;
    }
}
