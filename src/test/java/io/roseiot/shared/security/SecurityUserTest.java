package io.roseiot.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SecurityUserTest {

    @Test
    void snapshotsAuthoritiesAndPreventsMutationAfterAuthentication() {
        var source = new ArrayList<>(List.of(new SimpleGrantedAuthority("tenant.user.manage")));
        var principal = new SecurityUser(1L, 2L, "user@example.com", true, null, source);

        source.clear();

        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("tenant.user.manage");
        assertThatThrownBy(() -> principal.getAuthorities().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
