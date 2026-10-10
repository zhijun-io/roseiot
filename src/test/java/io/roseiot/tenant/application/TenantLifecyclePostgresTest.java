package io.roseiot.tenant.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.iam.domain.TokenDigests;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.infra.notifier.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Real wiring for slice 1: Flyway-created tenant tables, MyBatis persistence and transactional
 * atomicity of tenant plus initial TenantAdmin notifier against PostgreSQL.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@Transactional
class TenantLifecyclePostgresTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TenantInvitationNotifier tenantInvitationNotifier;

    @Test
    void shouldPersistTenantAndPendingInvitationTogether() {
        Tenant created = tenantService.create("Acme HVAC", "acme-hvac", "Ops@RoseIoT.io");

        Map<String, Object> tenantRow = jdbcTemplate.queryForMap(
                "SELECT slug, name, disabled_at FROM tenant WHERE id = ?", created.getId());
        assertThat(tenantRow)
                .containsEntry("slug", "acme-hvac")
                .containsEntry("name", "Acme HVAC")
                .containsEntry("disabled_at", null);

        ArgumentCaptor<String> plainToken = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier).sendInvitation(eq("ops@roseiot.io"), eq("Administrator"), plainToken.capture());

        Map<String, Object> invitationRow = jdbcTemplate.queryForMap(
                "SELECT i.email, r.code AS role_code, i.token_hash, i.expires_at, i.accepted_at, i.revoked_at"
                        + " FROM tenant_invitation i JOIN \"role\" r ON r.id = i.role_id"
                        + " WHERE i.tenant_id = ?", created.getId());
        assertThat(invitationRow)
                .containsEntry("email", "ops@roseiot.io")
                .containsEntry("role_code", "TENANT_ADMIN")
                .containsEntry("token_hash", TokenDigests.sha256Hex(plainToken.getValue()))
                .containsEntry("accepted_at", null)
                .containsEntry("revoked_at", null);
        Instant createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM tenant_invitation WHERE tenant_id = ?",
                Instant.class, created.getId());
        Instant expiresAt = ((Timestamp) invitationRow.get("expires_at")).toInstant();
        assertThat(expiresAt).isAfterOrEqualTo(createdAt.plusSeconds(7 * 24 * 3600 - 60));
    }

    @Test
    void shouldRejectDuplicateSlugWithoutWritingAnything() {
        tenantService.create("Acme HVAC", "acme-dup", "ops-a@roseiot.io");

        assertThatThrownBy(() -> tenantService.create("Acme Second", "acme-dup", "ops-b@roseiot.io"))
                .isInstanceOf(BusinessException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant WHERE slug = ?", Integer.class, "acme-dup")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_invitation i JOIN tenant t ON t.id = i.tenant_id"
                        + " WHERE t.slug = ?", Integer.class, "acme-dup")).isEqualTo(1);
    }

    @Test
    void shouldPersistRenameDisableAndEnableThroughRealSql() {
        Tenant created = tenantService.create("Acme HVAC", "acme-toggle", "ops@roseiot.io");

        tenantService.updateTenant(created.getId(), "Acme Climate", true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM tenant WHERE id = ?", String.class, created.getId()))
                .isEqualTo("Acme Climate");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT disabled_at FROM tenant WHERE id = ?", Instant.class, created.getId()))
                .isNotNull();

        tenantService.updateTenant(created.getId(), null, false);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT disabled_at FROM tenant WHERE id = ?", Instant.class, created.getId()))
                .isNull();
    }
}
