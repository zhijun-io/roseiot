package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import io.roseiot.tenant.persistence.TenantUserInvitation;
import io.roseiot.tenant.persistence.TenantUserInvitationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantServiceTests {

    private static final UUID TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000b1");

    private TenantMapper tenantMapper;
    private TenantUserInvitationMapper invitationMapper;
    private TenantInvitationNotifier invitationNotifier;
    private TenantService cut;

    @BeforeEach
    void setUp() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Tenant.class);
        tenantMapper = mock(TenantMapper.class);
        invitationMapper = mock(TenantUserInvitationMapper.class);
        invitationNotifier = mock(TenantInvitationNotifier.class);
        cut = new TenantService(tenantMapper, invitationMapper, invitationNotifier);
    }

    private void noExistingSlug() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
    }

    private Tenant existingTenant(Instant disabledAt) {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setSlug("acme-hvac");
        tenant.setName("Acme HVAC");
        tenant.setDisabledAt(disabledAt);
        tenant.setCreatedAt(Instant.parse("2026-10-08T00:00:00Z"));
        return tenant;
    }

    @Test
    void shouldCreateTenantAndPendingTenantAdminInvitationAtomically() {
        noExistingSlug();

        Tenant created = cut.create("Acme HVAC", "acme-hvac", " Ops@RoseIoT.io ");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantMapper).insert(tenantCaptor.capture());
        Tenant stored = tenantCaptor.getValue();
        assertThat(stored.getId()).isNotNull().isEqualTo(created.getId());
        assertThat(stored.getName()).isEqualTo("Acme HVAC");
        assertThat(stored.getSlug()).isEqualTo("acme-hvac");
        assertThat(stored.getDisabledAt()).isNull();
        assertThat(stored.getCreatedAt()).isNotNull();

        ArgumentCaptor<TenantUserInvitation> invitationCaptor =
                ArgumentCaptor.forClass(TenantUserInvitation.class);
        verify(invitationMapper).insert(invitationCaptor.capture());
        TenantUserInvitation invitation = invitationCaptor.getValue();
        assertThat(invitation.getTenantId()).isEqualTo(stored.getId());
        assertThat(invitation.getEmail()).isEqualTo("ops@roseiot.io");
        assertThat(invitation.getTargetRole()).isEqualTo("TenantAdmin");
        assertThat(invitation.getTokenHash()).hasSize(64).doesNotContain(" ");
        assertThat(invitation.getAcceptedAt()).isNull();
        assertThat(invitation.getRevokedAt()).isNull();
        assertThat(Duration.between(
                invitation.getCreatedAt(), invitation.getExpiresAt())).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void shouldHandPlainInvitationTokenOnlyToTheNotifier() {
        noExistingSlug();

        cut.create("Acme HVAC", "acme-hvac", "ops@roseiot.io");

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(invitationNotifier).sendInvitation(eq("ops@roseiot.io"), eq("TenantAdmin"), token.capture());
        assertThat(token.getValue()).isNotBlank();

        ArgumentCaptor<TenantUserInvitation> invitation = ArgumentCaptor.forClass(TenantUserInvitation.class);
        verify(invitationMapper).insert(invitation.capture());
        assertThat(invitation.getValue().getTokenHash())
                .isNotEqualTo(token.getValue())
                .isEqualTo(TokenDigests.sha256Hex(token.getValue()));
    }

    @Test
    void shouldRejectCreateWhenSlugAlreadyTaken() {
        when(tenantMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> cut.create("Acme HVAC", "acme-hvac", "ops@roseiot.io"))
                .isInstanceOf(BusinessException.class);

        verify(tenantMapper, never()).insert(any(Tenant.class));
        verifyNoInteractions(invitationMapper, invitationNotifier);
    }

    @Test
    void shouldTranslateConcurrentSlugInsertIntoConflictWithoutNotifying() {
        noExistingSlug();
        when(tenantMapper.insert(any(Tenant.class)))
                .thenThrow(new DuplicateKeyException("tenant_slug_uq"));

        assertThatThrownBy(() -> cut.create("Acme HVAC", "acme-hvac", "ops@roseiot.io"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(invitationNotifier);
        verify(invitationMapper, never()).insert(any(TenantUserInvitation.class));
    }

    @Test
    void shouldRejectUpdateOfUnknownTenant() {
        when(tenantMapper.selectById(UUID.randomUUID())).thenReturn(null);

        assertThatThrownBy(() -> cut.updateTenant(UUID.randomUUID(), "New name", null))
                .isInstanceOf(BusinessException.class);

        verify(tenantMapper, never()).update(isNull(), any());
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true"})
    void shouldToggleDisabledStateOnlyWhenItActuallyChanges(boolean requested, boolean alreadyDisabled) {
        Instant before = alreadyDisabled ? Instant.parse("2026-10-08T00:00:00Z") : null;
        Tenant after = existingTenant(requested ? Instant.now() : null);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(existingTenant(before), after);

        Tenant result = cut.updateTenant(TENANT_ID, null, requested);

        verify(tenantMapper).update(isNull(), any());
        assertThat(result.getDisabledAt() != null).isEqualTo(requested);
    }

    @Test
    void shouldKeepTenantUntouchedWhenRequestedStateMatchesCurrentState() {
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(existingTenant(null));

        Tenant result = cut.updateTenant(TENANT_ID, "  ", false);

        assertThat(result.getDisabledAt()).isNull();
        verify(tenantMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRenameTenantAndReturnRefreshedState() {
        Tenant renamed = existingTenant(null);
        renamed.setName("Acme Climate");
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(existingTenant(null), renamed);

        Tenant result = cut.updateTenant(TENANT_ID, " Acme Climate ", null);

        verify(tenantMapper).update(isNull(), any());
        assertThat(result.getName()).isEqualTo("Acme Climate");
    }
}
