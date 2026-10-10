package io.roseiot.iam.infra.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/** Resolves {@link SecurityUser} from the Spring Security context. */
public final class SecurityUserUtils {

    private SecurityUserUtils() {}

    public static Optional<SecurityUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return optionalFrom(authentication);
    }

    public static SecurityUser from(Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("Forbidden");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser details) {
            return details;
        }
        if (principal instanceof Jwt jwt) {
            return SecurityUser.fromJwtClaimsOnly(jwt);
        }
        throw new AccessDeniedException("Forbidden");
    }

    private static Optional<SecurityUser> optionalFrom(Authentication authentication) {
        try {
            return Optional.of(from(authentication));
        } catch (AccessDeniedException ignored) {
            return Optional.empty();
        }
    }
}
