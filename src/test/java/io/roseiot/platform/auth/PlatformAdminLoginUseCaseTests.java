package io.roseiot.platform.auth;

import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSession;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminLoginUseCaseTests {

    private static final String PASSWORD = "password1234";

    @Mock
    PlatformAdminMapper platformAdminMapper;

    @Mock
    PlatformAdminRefreshSessionMapper refreshSessionMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    PlatformAuthTokenIssuer tokenIssuer;

    @InjectMocks
    PlatformAdminLoginUseCase loginUseCase;

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                    '  Admin@Example.COM ', admin@example.com
                    a@b.com, a@b.com
                    """)
    void normalizeEmail(String raw, String expected) {
        assertThat(PlatformAdminLoginUseCase.normalizeEmail(raw)).isEqualTo(expected);
    }

    @Test
    void loginRejectsUnknownAdminOrBadPassword() {
        when(platformAdminMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> loginUseCase.login("a@b.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);

        PlatformAdmin admin = adminWithHash("hash");
        when(platformAdminMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> loginUseCase.login("a@b.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginReturnsTokensAndStoresRefreshSession() {
        UUID adminId = UUID.randomUUID();
        PlatformAdmin admin = adminWithHash("hash");
        admin.setId(adminId);
        when(platformAdminMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
        when(tokenIssuer.issueAccessToken(any(), any())).thenReturn("access-jwt");

        PlatformAdminLoginUseCase.LoginTokens tokens = loginUseCase.login("a@b.com", PASSWORD);

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isNotBlank();
        verify(refreshSessionMapper).insert(any(PlatformAdminRefreshSession.class));
    }

    private static PlatformAdmin adminWithHash(String hash) {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setPasswordHash(hash);
        return admin;
    }
}
