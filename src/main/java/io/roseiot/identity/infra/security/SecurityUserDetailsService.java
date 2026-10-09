package io.roseiot.identity.infra.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRoleMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class SecurityUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    public SecurityUserDetailsService(UserMapper userMapper, UserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String email = LoginEmails.normalize(username);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        return toDetails(user, email);
    }

    public SecurityUser loadByUserId(UUID userId) {
        User user = userMapper.selectById(userId);
        return toDetails(user, userId.toString());
    }

    private SecurityUser toDetails(User user, String notFoundKey) {
        if (user == null || user.getDisabledAt() != null) {
            throw new UsernameNotFoundException(notFoundKey);
        }
        return new SecurityUser(user, RoseiotSecurityAuthorities.forUser(userRoleMapper, user.getId()));
    }
}
