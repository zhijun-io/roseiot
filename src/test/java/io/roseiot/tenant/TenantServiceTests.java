package io.roseiot.tenant;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.roseiot.shared.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTests {
    private static final long TENANT_ID = 42L;
    private static final String TENANT_CODE = "customer-42";
    private TenantMapper tenantMapper;
    private TenantService service;

    @BeforeEach
    void setUp() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TenantEntity.class);
        tenantMapper = mock(TenantMapper.class);
        service = new TenantService(tenantMapper);
    }

    @Test
    void createUsesGeneratedIdAndCreatesExternalCode() {
        when(tenantMapper.insert(any(TenantEntity.class))).thenAnswer(invocation -> {
            TenantEntity entity = invocation.getArgument(0);
            entity.setId(TENANT_ID);
            return 1;
        });
        Tenant tenant = service.create(" Acme HVAC ");
        assertThat(tenant.id()).isEqualTo(TENANT_ID);
        assertThat(tenant.code()).hasSize(16).matches("[A-Za-z0-9_-]+");
        assertThat(tenant.name()).isEqualTo("Acme HVAC");
    }

    @Test
    void updateByIdRejectsUnknownTenant() {
        when(tenantMapper.selectByIdForUpdate(TENANT_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.update(TENANT_ID, "Name", null))
                .isInstanceOf(BusinessException.class);
        verify(tenantMapper, never()).updateById(any(TenantEntity.class));
    }

    @Test
    void updateByIdDoesNotAllowChangingDefaultTenant() {
        TenantEntity entity = new TenantEntity();
        entity.setId(Tenant.DEFAULT_TENANT_ID);
        when(tenantMapper.selectByIdForUpdate(Tenant.DEFAULT_TENANT_ID)).thenReturn(entity);
        assertThatThrownBy(() -> service.update(Tenant.DEFAULT_TENANT_ID, "Name", null))
                .isInstanceOf(AccessDeniedException.class);
        verify(tenantMapper, never()).updateById(any(TenantEntity.class));
    }

    @Test
    void updateByIdChangesNameAndDisabledState() {
        TenantEntity entity = new TenantEntity();
        entity.setId(TENANT_ID);
        entity.setCode(TENANT_CODE);
        entity.setName("Acme HVAC");
        entity.setCreatedAt(Instant.now());
        entity.setStatus(TenantStatus.ACTIVE);
        when(tenantMapper.selectByIdForUpdate(TENANT_ID)).thenReturn(entity);
        Tenant updated = service.update(TENANT_ID, "Acme Climate", true);
        assertThat(updated.name()).isEqualTo("Acme Climate");
        assertThat(updated.status()).isEqualTo(TenantStatus.DISABLED);
        verify(tenantMapper).updateById(entity);
    }
}
