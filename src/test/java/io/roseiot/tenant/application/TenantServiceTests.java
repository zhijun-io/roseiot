package io.roseiot.tenant.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.roseiot.iam.application.RoleService;
import io.roseiot.iam.persistence.RoleMapper;
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
import org.springframework.security.access.AccessDeniedException;

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
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000f1");
    private static final UUID TENANT_ADMIN_ROLE_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000a1");

    private TenantMapper tenantMapper;
    private TenantInvitationService invitationService;
    private RoleMapper roleMapper;
    private RoleService roleService;
    private TenantService cut;

    @BeforeEach
    void setUp() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Tenant.class);
        tenantMapper = mock(TenantMapper.class);
        invitationService = mock(TenantInvitationService.class);
        roleMapper = mock(RoleMapper.class);
        roleService = mock(RoleService.class);
        cut = new TenantService(tenantMapper, invitationService, roleMapper, roleService);
        when(tenantMapper.requireDefaultTenantId()).thenReturn(DEFAULT_TENANT_ID);
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
    void shouldCreateTenantAndInviteInitialAdministrator() {
        noExistingSlug();
        when(roleMapper.requireIdByCode(any(UUID.class), eq(RoleService.TENANT_ADMIN)))
                .thenReturn(TENANT_ADMIN_ROLE_ID);

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
                .issueInvitation(stored.getId(), "ops@roseiot.io", TENANT_ADMIN_ROLE_ID);
        verify(roleService).initializeBuiltInRoles(stored.getId());
    }

    @Test
    void shouldRejectCreatingTheReservedDefaultSlug() {
        assertThatThrownBy(() -> cut.create("Copy", "default", "ops@roseiot.io"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(invitationService, roleMapper, roleService);
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
    void shouldTranslateConcurrentSlugInsertIntoConflictWithoutInviting() {
        noExistingSlug();
        when(tenantMapper.insert(any(Tenant.class)))
                .thenThrow(new DuplicateKeyException("tenant_slug_uq"));

        assertThatThrownBy(() -> cut.create("Acme HVAC", "acme-hvac", "ops@roseiot.io"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(invitationService);
    }

    @Test
    void shouldRejectUpdateOfUnknownTenant() {
        when(tenantMapper.selectById(any(UUID.class))).thenReturn(null);

        assertThatThrownBy(() -> cut.updateTenant(UUID.randomUUID(), "New name", null))
                .isInstanceOf(BusinessException.class);

        verify(tenantMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRejectTouchingTheReservedDefaultTenant() {
        assertThatThrownBy(() -> cut.updateTenant(DEFAULT_TENANT_ID, "Hijack", null))
                .isInstanceOf(AccessDeniedException.class);
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
