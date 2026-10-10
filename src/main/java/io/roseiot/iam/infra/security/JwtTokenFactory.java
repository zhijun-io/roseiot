package io.roseiot.iam.infra.security;

import io.roseiot.iam.persistence.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class JwtTokenFactory {

    public static final String AUTHORIZATION_VERSION = "azv";
    public static final String SESSION_ID = "sid";
    public static final String TENANT_ID = "tid";
    public static final String FAMILY_ID = "fid";
    public static final String TOKEN_TYPE = "token_type";
    public static final String REFRESH_TOKEN = "refresh";

    private static final long ACCESS_TOKEN_MINUTES = 15;
    private static final long REFRESH_TOKEN_DAYS = 7;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenFactory(JwtEncoder encoder, JwtDecoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public String createAccessToken(User user, UUID sessionId, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim(SESSION_ID, sessionId.toString())
                .claim(TENANT_ID, java.util.Objects.requireNonNull(user.getTenantId()).toString())
                .claim(AUTHORIZATION_VERSION, user.getAuthorizationVersion())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES))
                .build();
        return encode(claims);
    }

    public String createRefreshToken(UUID userId, UUID sessionId, UUID familyId, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim(SESSION_ID, sessionId.toString())
                .claim(FAMILY_ID, familyId.toString())
                .claim(TOKEN_TYPE, REFRESH_TOKEN)
                .issuedAt(now)
                .expiresAt(now.plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS))
                .build();
        return encode(claims);
    }

    public RefreshClaims decodeRefreshToken(String rawToken) {
        try {
            Jwt jwt = decoder.decode(rawToken);
            if (!REFRESH_TOKEN.equals(jwt.getClaimAsString(TOKEN_TYPE))) {
                throw AuthenticationExceptionMapping.invalidRefreshToken();
            }
            return new RefreshClaims(
                    UUID.fromString(jwt.getSubject()),
                    UUID.fromString(jwt.getClaimAsString(SESSION_ID)),
                    UUID.fromString(jwt.getClaimAsString(FAMILY_ID)));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw AuthenticationExceptionMapping.invalidRefreshToken();
        }
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public record RefreshClaims(UUID userId, UUID sessionId, UUID familyId) {}
}
