package io.roseiot.shared.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class JwtTokenFactory {

    public static final String TENANT_ID = "tid";

    private static final long ACCESS_TOKEN_MINUTES = 15;

    private final JwtEncoder encoder;

    public JwtTokenFactory(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String createAccessToken(java.lang.Long userId, Long tenantId, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim(TENANT_ID, java.util.Objects.requireNonNull(tenantId).toString())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES))
                .build();
        return encode(claims);
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
