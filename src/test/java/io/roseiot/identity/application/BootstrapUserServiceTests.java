package io.roseiot.identity.application;

import io.roseiot.identity.domain.AlreadyBootstrappedException;
import io.roseiot.identity.domain.BootstrapValidationException;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapUserServiceTests {

    private static final String PASSWORD = "password1234";

    @Mock
    UserMapper userMapper;

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    BootstrapUserService bootstrapService;

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                    '  Admin@Example.COM ', admin@example.com
                    a@example.com, a@example.com
                    """)
    void normalizeEmail(String raw, String expected) {
        assertThat(BootstrapUserService.normalizeEmail(raw)).isEqualTo(expected);
    }

    @Test
    void bootstrapValidatesInputAndSinglePlatformUserInvariant() {
        assertThatThrownBy(() -> bootstrapService.bootstrap(null, PASSWORD))
                .isInstanceOf(BootstrapValidationException.class);

        when(userMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> bootstrapService.bootstrap("a@example.com", "short1"))
                .isInstanceOf(BootstrapValidationException.class);
        verify(userMapper, never()).insert(any(User.class));

        when(userMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> bootstrapService.bootstrap("a@example.com", PASSWORD))
                .isInstanceOf(AlreadyBootstrappedException.class);
    }

    @Test
    void bootstrapPersistsNormalizedUser() {
        UUID systemAdminRoleId = UUID.randomUUID();
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), eq("SystemAdmin")))
                .thenReturn(systemAdminRoleId);
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        bootstrapService.bootstrap("Admin@Example.com", PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User stored = captor.getValue();
        assertThat(stored.getEmail()).isEqualTo("admin@example.com");
        assertThat(stored.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(stored.getPasswordAlgorithm()).isEqualTo("bcrypt");
        assertThat(stored.getAuthorizationVersion()).isEqualTo(1);
        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getId().version()).isEqualTo(7);
        assertThat(stored.getCreatedAt()).isNotNull();
        verify(jdbcTemplate).update(anyString(), any(), eq(stored.getId()), eq(systemAdminRoleId));
    }
}
