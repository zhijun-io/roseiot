package io.roseiot.tenant.application;

import io.roseiot.identity.application.UserRefreshSessions;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.tenant.persistence.TenantUserInvitation;
import io.roseiot.tenant.persistence.TenantUserInvitationMapper;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantInvitationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000a1");

    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private TenantUserInvitationMapper invitationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TenantInvitationNotifier invitationNotifier;
    @Mock
    private UserRefreshSessions refreshSessions;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private TenantInvitationService cut;

    private void stubActiveTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        lenient().doNothing().when(tenantMapper).requireActive(TENANT_ID);
        lenient().doReturn(tenant).when(tenantMapper).selectById(TENANT_ID);
    }

    @Test
    void create_shouldRejectExistingUserEmail() {
        stubActiveTenant();
        given(userMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> cut.create(TENANT_ID, "ops@roseiot.io", "TenantUser"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_shouldPersistInvitationAndNotify() {
        stubActiveTenant();
        given(userMapper.selectCount(any())).willReturn(0L);

        cut.create(TENANT_ID, "ops@roseiot.io", "TenantUser");

        ArgumentCaptor<TenantUserInvitation> captor = ArgumentCaptor.forClass(TenantUserInvitation.class);
        verify(invitationMapper).insert(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ops@roseiot.io");
        assertThat(captor.getValue().getTargetRole()).isEqualTo("TenantUser");
        verify(invitationNotifier).sendInvitation(eq("ops@roseiot.io"), eq("TenantUser"), any());
    }

    @Test
    void accept_shouldRejectUnknownToken() {
        given(invitationMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> cut.acceptInvitation("bad-token", "newSecurePass9012"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void check_shouldMarkExpiredInvitation() {
        TenantUserInvitation row = pendingInvitation("token");
        row.setExpiresAt(Instant.now().minusSeconds(60));
        when(invitationMapper.selectOne(any())).thenReturn(row);

        assertThat(cut.checkInvitationToken("token"))
                .isEqualTo(TenantInvitationService.InvitationTokenState.EXPIRED);
    }

    @Test
    void check_shouldMarkValidInvitation() {
        stubActiveTenant();
        when(invitationMapper.selectOne(any())).thenReturn(pendingInvitation("token"));

        assertThat(cut.checkInvitationToken("token"))
                .isEqualTo(TenantInvitationService.InvitationTokenState.VALID);
    }

    private TenantUserInvitation pendingInvitation(String plainToken) {
        TenantUserInvitation invitation = new TenantUserInvitation();
        invitation.setTenantId(TENANT_ID);
        invitation.setTokenHash(TokenDigests.sha256Hex(plainToken));
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        return invitation;
    }
}
