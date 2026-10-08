package io.roseiot.platform.bootstrap;

import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapPlatformAdminUseCaseTests {

    private static final String PASSWORD = "password1234";

    @Mock
    PlatformAdminMapper platformAdminMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    BootstrapPlatformAdminUseCase bootstrapUseCase;

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                    '  Admin@Example.COM ', admin@example.com
                    a@example.com, a@example.com
                    """)
    void normalizeEmail(String raw, String expected) {
        assertThat(BootstrapPlatformAdminUseCase.normalizeEmail(raw)).isEqualTo(expected);
    }

    @Test
    void bootstrapValidatesInputAndSingleAdminInvariant() {
        assertThatThrownBy(() -> bootstrapUseCase.bootstrap(null, PASSWORD))
                .isInstanceOf(BootstrapValidationException.class);

        when(platformAdminMapper.selectCount(null)).thenReturn(0L);
        assertThatThrownBy(() -> bootstrapUseCase.bootstrap("a@example.com", "short1"))
                .isInstanceOf(BootstrapValidationException.class);
        verify(platformAdminMapper, never()).insert(any(PlatformAdmin.class));

        when(platformAdminMapper.selectCount(null)).thenReturn(1L);
        assertThatThrownBy(() -> bootstrapUseCase.bootstrap("a@example.com", PASSWORD))
                .isInstanceOf(AlreadyBootstrappedException.class);
    }

    @Test
    void bootstrapPersistsNormalizedAdmin() {
        when(platformAdminMapper.selectCount(null)).thenReturn(0L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");

        bootstrapUseCase.bootstrap("Admin@Example.com", PASSWORD);

        ArgumentCaptor<PlatformAdmin> captor = ArgumentCaptor.forClass(PlatformAdmin.class);
        verify(platformAdminMapper).insert(captor.capture());
        PlatformAdmin stored = captor.getValue();
        assertThat(stored.getEmail()).isEqualTo("admin@example.com");
        assertThat(stored.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(stored.getPasswordAlgorithm()).isEqualTo("bcrypt");
        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getId().version()).isEqualTo(7);
        assertThat(stored.getCreatedAt()).isNotNull();
    }
}
