package io.roseiot.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRefreshSession;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import io.roseiot.shared.util.UuidV7;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class UserRefreshSessions {

    private final UserRefreshSessionMapper refreshSessionMapper;
    private final UserMapper userMapper;
    private final JwtEncoder jwtEncoder;

    public UserRefreshSessions(
            UserRefreshSessionMapper refreshSessionMapper, UserMapper userMapper, JwtEncoder jwtEncoder) {
        this.refreshSessionMapper = refreshSessionMapper;
        this.userMapper = userMapper;
        this.jwtEncoder = jwtEncoder;
    }

    public UserSessionService.SessionTokens issueNewFamily(UUID userId, Instant now) {
        UUID sessionId = UuidV7.generate();
        UUID familyId = UuidV7.generate();
        String refreshToken = TokenDigests.generateUrlSafe256Bit();
        insertSession(sessionId, userId, familyId, refreshToken, now);
        return new UserSessionService.SessionTokens(issueAccessToken(userId, sessionId), refreshToken);
    }

    UserSessionService.SessionTokens rotate(UserRefreshSession current, Instant now) {
        if (refreshSessionMapper.revokeForRotation(current.getId(), now) != 1) {
            return null;
        }

        UUID newSessionId = UuidV7.generate();
        String refreshToken = TokenDigests.generateUrlSafe256Bit();
        insertSession(newSessionId, current.getUserId(), current.getFamilyId(), refreshToken, now);
        return new UserSessionService.SessionTokens(
                issueAccessToken(current.getUserId(), newSessionId), refreshToken);
    }

    void revokeSession(UUID sessionId, Instant revokedAt) {
        if (sessionId == null) {
            return;
        }
        UserRefreshSession session = refreshSessionMapper.selectById(sessionId);
        if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(revokedAt);
            refreshSessionMapper.updateById(session);
        }
    }

    void revokeAllForUser(UUID userId, Instant revokedAt) {
        revokeSessions(refreshSessionMapper.selectList(
                new LambdaQueryWrapper<UserRefreshSession>()
                        .eq(UserRefreshSession::getUserId, userId)
                        .isNull(UserRefreshSession::getRevokedAt)), revokedAt);
    }

    void revokeFamily(UUID familyId, Instant revokedAt) {
        revokeSessions(refreshSessionMapper.selectList(
                new LambdaQueryWrapper<UserRefreshSession>()
                        .eq(UserRefreshSession::getFamilyId, familyId)
                        .isNull(UserRefreshSession::getRevokedAt)), revokedAt);
    }

    private void insertSession(
            UUID sessionId, UUID userId, UUID familyId, String plainRefreshToken, Instant now) {
        UserRefreshSession session = new UserRefreshSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setFamilyId(familyId);
        session.setTokenHash(TokenDigests.sha256Hex(plainRefreshToken));
        session.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
        session.setCreatedAt(now);
        refreshSessionMapper.insert(session);
    }

    private void revokeSessions(List<UserRefreshSession> activeSessions, Instant revokedAt) {
        for (UserRefreshSession session : activeSessions) {
            session.setRevokedAt(revokedAt);
            refreshSessionMapper.updateById(session);
        }
    }

    private String issueAccessToken(UUID userId, UUID sessionId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalStateException("Cannot issue access token for missing user");
        }
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, user.getAuthorizationVersion())
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES));
        JwtClaimsSet builtClaims = claims.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, builtClaims)).getTokenValue();
    }
}
