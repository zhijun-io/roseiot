package io.roseiot.iam.infra.security;

import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.persistence.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityJwtAuthenticationConverterTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private SecurityUserDetailsService details;
    private IdentityJwtAuthenticationConverter cut;

    @BeforeEach
    void setUp() {
        details = mock(SecurityUserDetailsService.class);
        cut = new IdentityJwtAuthenticationConverter(details);
    }

    private static Jwt token(String subject, String tenant) {
        Instant now = Instant.now();
        return new Jwt("raw", now, now.plus(15, ChronoUnit.MINUTES), Map.of("alg", "HS256"),
                Map.of("sub", subject, "tid", tenant));
    }

    private static SecurityUser user(UUID tenantId) {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(tenantId);
        user.setEmail("ops@roseiot.io");
        return new SecurityUser(user, List.of(new SimpleGrantedAuthority(ActionCode.DEVICE_READ)));
    }

    @Test
    void authenticatesAnActiveUserAndLoadsCurrentAuthorities() {
        when(details.loadByUserId(USER_ID)).thenReturn(user(TENANT_ID));
        Authentication result = cut.convert(token(USER_ID.toString(), TENANT_ID.toString()));
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isInstanceOf(SecurityUser.class);
        assertThat(result.getAuthorities()).extracting(Object::toString).contains(ActionCode.DEVICE_READ);
    }

    @Test
    void rejectsUnknownUser() {
        when(details.loadByUserId(USER_ID)).thenThrow(new UsernameNotFoundException("missing"));
        assertThatThrownBy(() -> cut.convert(token(USER_ID.toString(), TENANT_ID.toString())))
                .isInstanceOf(OAuth2AuthenticationException.class).hasMessageContaining("User not active");
    }

    @Test
    void rejectsInvalidSubjectWithoutLookingUpUser() {
        assertThatThrownBy(() -> cut.convert(token("not-a-user-id", TENANT_ID.toString())))
                .isInstanceOf(OAuth2AuthenticationException.class).hasMessageContaining("Invalid subject");
        verifyNoInteractions(details);
    }

    @Test
    void rejectsTenantMismatch() {
        when(details.loadByUserId(USER_ID)).thenReturn(user(TENANT_ID));
        assertThatThrownBy(() -> cut.convert(token(USER_ID.toString(), UUID.randomUUID().toString())))
                .isInstanceOf(OAuth2AuthenticationException.class).hasMessageContaining("Tenant mismatch");
    }
}
