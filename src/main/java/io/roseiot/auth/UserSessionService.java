package io.roseiot.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserSessionService implements SessionLifecycle {

    private static final String ISSUER = "roseiot";
    private static final String AUDIENCE = "roseiot-api";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSessionMapper sessions;
    private final SessionRefreshTokenMapper refreshTokens;
    private final AuthJwtProperties properties;
    private final JwtEncoder encoder;

    UserSessionService(
            UserSessionMapper sessions,
            SessionRefreshTokenMapper refreshTokens,
            AuthJwtProperties properties,
            JwtEncoder encoder) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public SessionTokens createSession(long userId, boolean passwordChangeRequired) {
        Instant now = Instant.now();
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        long sessionId;
        String refreshToken = newRefreshToken();

        UserSessionRow session = new UserSessionRow();
        session.setId(null);
        session.setUserId(userId);
        session.setCreatedAt(now);
        session.setExpiresAt(refreshExpiresAt);
        sessions.insert(session);
        sessionId = session.getId();

        SessionRefreshTokenRow refreshTokenRow = new SessionRefreshTokenRow();
        refreshTokenRow.setId(null);
        refreshTokenRow.setSessionId(sessionId);
        refreshTokenRow.setTokenDigest(digest(refreshToken));
        refreshTokenRow.setIssuedAt(now);
        refreshTokenRow.setExpiresAt(refreshExpiresAt);
        refreshTokens.insert(refreshTokenRow);

        return new SessionTokens(
                sessionId,
                issueAccessToken(sessionId, userId, passwordChangeRequired),
                refreshToken,
                properties.accessTokenTtl().toSeconds(),
                properties.refreshTokenTtl().toSeconds(),
                passwordChangeRequired);
    }

    @Override
    public String issueAccessToken(long sessionId, long userId, boolean passwordChangeRequired) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(java.util.List.of(AUDIENCE))
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("sid", Long.toString(sessionId))
                .claim("password_change_required", passwordChangeRequired)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Override
    @Transactional
    public void revokeAllForUser(long userId) {
        sessions.revokeAllForUser(userId);
        refreshTokens.revokeRefreshTokensForUser(userId);
    }

    @Override
    public boolean isActive(long sessionId, long userId, Instant now) {
        return sessions.isActive(sessionId, userId, now);
    }

    static String digest(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String newRefreshToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
