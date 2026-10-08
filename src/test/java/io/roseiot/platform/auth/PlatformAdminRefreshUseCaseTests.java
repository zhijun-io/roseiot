package io.roseiot.platform.auth;

import io.roseiot.platform.persistence.PlatformAdminRefreshSession;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminRefreshUseCaseTests {

    @Mock
    PlatformAdminRefreshSessionMapper refreshSessionMapper;

    @Mock
    PlatformAuthTokenIssuer tokenIssuer;

    @InjectMocks
    PlatformAdminRefreshUseCase refreshUseCase;

    @Test
    void replayOfRevokedRefreshRevokesFamily() {
        UUID familyId = UUID.randomUUID();
        String rawToken = "replay-token-value";
        String hash = TokenDigests.sha256Hex(rawToken);

        PlatformAdminRefreshSession revoked = session(familyId, hash);
        revoked.setRevokedAt(Instant.now());

        when(refreshSessionMapper.selectOne(any())).thenReturn(null, revoked);
        when(refreshSessionMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> refreshUseCase.refresh(rawToken)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshSessionMapper).selectList(any());
    }

    @Test
    void validRefreshRotatesSession() {
        UUID familyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String rawToken = "valid-refresh-token";
        String hash = TokenDigests.sha256Hex(rawToken);

        PlatformAdminRefreshSession active = session(familyId, hash);
        active.setPlatformAdminId(adminId);
        active.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

        when(refreshSessionMapper.selectOne(any())).thenReturn(active);
        when(tokenIssuer.issueAccessToken(any(), any())).thenReturn("access-jwt");

        PlatformAdminLoginUseCase.LoginTokens tokens = refreshUseCase.refresh(rawToken);

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isNotBlank().isNotEqualTo(rawToken);

        ArgumentCaptor<PlatformAdminRefreshSession> insertCaptor =
                ArgumentCaptor.forClass(PlatformAdminRefreshSession.class);
        verify(refreshSessionMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getFamilyId()).isEqualTo(familyId);
    }

    private static PlatformAdminRefreshSession session(UUID familyId, String hash) {
        PlatformAdminRefreshSession session = new PlatformAdminRefreshSession();
        session.setId(UUID.randomUUID());
        session.setFamilyId(familyId);
        session.setTokenHash(hash);
        return session;
    }
}
