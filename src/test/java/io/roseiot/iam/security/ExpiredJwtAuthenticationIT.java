package io.roseiot.iam.security;

import io.roseiot.TestcontainersConfig;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfig.class)
class ExpiredJwtAuthenticationIT {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void returnsProblemDetailUnauthorizedForExpiredSignedBearerToken() {
        Instant now = Instant.now();
        String expiredToken = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .issuer("https://roseiot.local")
                                .subject("1001")
                                .audience(java.util.List.of("roseiot-api"))
                                .issuedAt(now.minus(10, ChronoUnit.MINUTES))
                                .expiresAt(now.minus(5, ChronoUnit.MINUTES))
                                .build()))
                .getTokenValue();

        restTestClient
                .get()
                .uri("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("invalid_token");
    }
}
