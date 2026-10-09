package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

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
    private TenantInvitationService invitationService;
    private TenantService cut;

    @BeforeEach
    void setUp() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Tenant.class);
        tenantMapper = mock(TenantMapper.class);
        invitationService = mock(TenantInvitationService.class);
        cut = new TenantService(tenantMapper, invitationService);
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

        verify(invitationService)
                .issueInvitation(stored.getId(), "ops@roseiot.io", TenantRoles.TENANT_ADMIN);
    }

    @Test
    void shouldRejectCreateWhenSlugAlreadyTaken() {
        when(tenantMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> cut.create("Acme HVAC", "acme-hvac", "ops@roseiot.io"))
                .isInstanceOf(BusinessException.class);

        verify(tenantMapper, never()).insert(any(Tenant.class));
        verifyNoInteractions(invitationService);
    }

    @Test
    void shouldTranslateConcurrentSlugInsertIntoConflictWithoutNotifying() {
        noExistingSlug();
        when(tenantMapper.insert(any(Tenant.class)))
                .thenThrow(new DuplicateKeyException("tenant_slug_uq"));

        assertThatThrownBy(() -> cut.create("Acme HVAC", "acme-hvac", "ops@roseiot.io"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(invitationService);
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
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(existingTenant(before));

        Tenant result = cut.updateTenant(TENANT_ID, null, requested);

        verify(tenantMapper).update(isNull(), any());
        assertThat(result.getDisabledAt() != null).isEqualTo(requested);
    }

    @Test
    void shouldPreserveOriginalDisabledAtWhenDisablingAnAlreadyDisabledTenant() {
        Instant originalDisabledAt = Instant.parse("2026-10-08T00:00:00Z");
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(existingTenant(originalDisabledAt));

        Tenant result = cut.updateTenant(TENANT_ID, null, true);

        assertThat(result.getDisabledAt()).isEqualTo(originalDisabledAt);
    }

    @Test
    void shouldRenameTenantAndReturnUpdatedState() {
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(existingTenant(null));

        Tenant result = cut.updateTenant(TENANT_ID, " Acme Climate ", null);

        verify(tenantMapper).update(isNull(), any());
        assertThat(result.getName()).isEqualTo("Acme Climate");
    }
}
