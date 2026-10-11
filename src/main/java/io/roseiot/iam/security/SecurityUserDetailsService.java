package io.roseiot.iam.security;

import io.roseiot.iam.user.UserService;
import io.roseiot.iam.user.UserAuthentication;
import io.roseiot.tenant.TenantService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class SecurityUserDetailsService implements UserDetailsService {

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

    SecurityUser loadByUserId(Long userId) {
        return toDetails(userService.findForAccessToken(userId), userId.toString());
    }

    private SecurityUser toDetails(UserAuthentication authenticationUser, String notFoundKey) {
        if (authenticationUser == null) throw new UsernameNotFoundException(notFoundKey);
        var user = authenticationUser.user();
        if (!tenantService.isActive(user.tenantId())) throw new UsernameNotFoundException(notFoundKey);
        var authorities = authenticationUser.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new SecurityUser(user, authenticationUser.password(), authorities);
    }
}
