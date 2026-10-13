package io.roseiot.shared.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class JwtTokenFactory {

    public static final String TENANT_ID = "tid";

    private static final long ACCESS_TOKEN_MINUTES = 15;

    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;

    public JwtTokenFactory(JwtEncoder encoder, String issuer, String audience) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String createAccessToken(Long userId, Long tenantId, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(java.util.Objects.requireNonNull(userId).toString())
                .audience(List.of(audience))
                .claim(TENANT_ID, java.util.Objects.requireNonNull(tenantId).toString())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES))
                .build();
        return encode(claims);
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
