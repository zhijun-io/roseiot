package io.roseiot.iam.user;

import io.roseiot.shared.security.SecurityUser;
import io.roseiot.shared.security.SecurityUserLoader;
import io.roseiot.tenant.TenantService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class SecurityUserDetailsService implements UserDetailsService, SecurityUserLoader {

    private final TenantService tenantService;
    private final UserService userService;

    public SecurityUserDetailsService(UserService userService, TenantService tenantService) {
        this.userService = userService;
        this.tenantService = tenantService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String email = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        return toDetails(userService.findForAuthentication(email), email);
    }

    @Override
    public SecurityUser loadByUserId(Long userId) {
        return toDetails(userService.findForAccessToken(userId), userId.toString());
    }

    private SecurityUser toDetails(Optional<UserAuthentication> authenticationUser, String notFoundKey) {
        return authenticationUser
                .filter(authentication -> tenantService.isActive(authentication.user().tenantId()))
                .map(authentication -> {
                    var authorities = authentication.permissions().stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    var user = authentication.user();
                    return new SecurityUser(user.id(), user.tenantId(), user.email(),
                            user.status() == UserStatus.ACTIVE, authentication.password(), authorities);
                })
                .orElseThrow(() -> new UsernameNotFoundException(notFoundKey));
    }
}
