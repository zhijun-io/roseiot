package io.roseiot.auth;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class UserDetailsServiceAdapter implements UserDetailsService {

    private final UserMapper users;

    UserDetailsServiceAdapter(UserMapper users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String normalizedEmail) {
        AccountCredentials account = users.findCredentialsByNormalizedEmail(normalizedEmail);
        if (account == null) {
            throw new UsernameNotFoundException("Account credentials were not found");
        }
        return new AuthPrincipal(
                account.getUserId(),
                account.getEmail(),
                account.getPasswordHash(),
                "ACTIVE".equals(account.getStatus()),
                account.isPasswordChangeRequired());
    }

    record AuthPrincipal(long userId, String email, String password, boolean active, boolean passwordChangeRequired)
            implements UserDetails {

        @Override
        public List<SimpleGrantedAuthority> getAuthorities() {
            return List.of();
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getUsername() {
            return email;
        }

        @Override
        public boolean isEnabled() {
            return active;
        }
    }
}
