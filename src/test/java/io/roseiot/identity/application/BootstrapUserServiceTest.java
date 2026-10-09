package io.roseiot.identity.application;

import io.roseiot.identity.domain.AlreadyBootstrappedException;
import io.roseiot.identity.domain.BootstrapValidationException;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapUserServiceTest {

    private static final UUID SYSTEM_ADMIN_ROLE_ID = UUID.fromString("019a0000-0000-7000-8000-0000000000a1");

    private UserMapper userMapper;
    private JdbcTemplate jdbcTemplate;
    private PasswordEncoder passwordEncoder;
    private BootstrapUserService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cut = new BootstrapUserService(userMapper, jdbcTemplate, passwordEncoder);
        when(userMapper.selectCount(any())).thenReturn(0L);
    }

    private void stubEmptyRoleTable() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), eq("SystemAdmin")))
                .thenReturn(SYSTEM_ADMIN_ROLE_ID);
    }

    @Test
    void shouldCreateUnscopedSystemAdministratorOnFirstBootstrap() {
        stubEmptyRoleTable();

        cut.bootstrap("  Ops@RoseIoT.io  ", "correct horse 42");

        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(inserted.capture());
        User created = inserted.getValue();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getEmail()).isEqualTo("ops@roseiot.io");
        assertThat(created.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(created.getPasswordAlgorithm()).isEqualTo("bcrypt");
        assertThat(created.getAuthorizationVersion()).isEqualTo(1);
        assertThat(created.getTenantId()).isNull();
        assertThat(created.getDisabledAt()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldGrantSystemAdminRoleWithoutTenantScope() {
        stubEmptyRoleTable();

        cut.bootstrap("ops@roseiot.io", "correct horse 42");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> values = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sql.capture(), values.capture(), values.capture(), values.capture());
        User created = insertedUser();
        assertThat(sql.getValue())
                .contains("INSERT INTO user_role")
                .contains("scope_tenant_id");
        assertThat(values.getAllValues()).hasSize(3);
        assertThat(values.getAllValues()).containsSequence(created.getId(), SYSTEM_ADMIN_ROLE_ID);
    }

    @Test
    void shouldRefuseSecondBootstrapWithoutWritingAnything() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> cut.bootstrap("ops@roseiot.io", "correct horse 42"))
                .isInstanceOf(AlreadyBootstrappedException.class);
        verify(userMapper, never()).insert(any(User.class));
        verifyNoRoleWrite();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is not a usable email")
    @ValueSource(strings = {"ops", "ops@", "@roseiot.io", "ops @roseiot.io", "ops@roseiot", "ops@roseiot."})
    void shouldRejectMalformedEmailBeforeWritingAnything(String email) {
        assertThatThrownBy(() -> cut.bootstrap(email, "correct horse 42"))
                .isInstanceOf(BootstrapValidationException.class)
                .hasMessage("Invalid email");
        verify(userMapper, never()).insert(any(User.class));
        verifyNoRoleWrite();
    }

    @Test
    void shouldRejectMissingEmailWithItsOwnMessage() {
        assertThatThrownBy(() -> cut.bootstrap(null, "correct horse 42"))
                .isInstanceOf(BootstrapValidationException.class)
                .hasMessage("Email is required");
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void shouldRejectWeakPasswordBeforeWritingAnyRow() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");

        assertThatThrownBy(() -> cut.bootstrap("ops@roseiot.io", "short1"))
                .isInstanceOf(BootstrapValidationException.class)
                .hasMessage("Password must be at least 12 characters");
        verify(userMapper, never()).insert(any(User.class));
        verifyNoRoleWrite();
    }

    @Test
    void shouldCheckBootstrapStateBeforeValidatingInput() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        // A bootstrapped system must not leak which field of the request was invalid.
        assertThatThrownBy(() -> cut.bootstrap("not-an-email", "short1"))
                .isInstanceOf(AlreadyBootstrappedException.class);
    }

    @Test
    void shouldNormalizeEmailForTheStoredLogin() {
        assertThat(BootstrapUserService.normalizeEmail("  Ops@RoseIoT.io  ")).isEqualTo("ops@roseiot.io");
    }

    private User insertedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void verifyNoRoleWrite() {
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
