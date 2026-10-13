package io.roseiot.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("roseiot.auth.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotBlank String audience) {

    @AssertTrue(message = "issuer must be an absolute HTTP(S) URL")
    public boolean isIssuerValid() {
        if (issuer == null || issuer.isBlank()) return false;
        try {
            URI uri = URI.create(issuer);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && (scheme.equals("http") || scheme.equals("https"))
                    && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=redacted, issuer=" + issuer + ", audience=" + audience + "]";
    }
}
