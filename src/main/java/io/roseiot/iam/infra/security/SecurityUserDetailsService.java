package io.roseiot.iam.infra.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.iam.domain.LoginEmails;
import io.roseiot.iam.domain.UserStatus;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserCredential;
import io.roseiot.iam.persistence.UserCredentialMapper;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.iam.persistence.UserRoleMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SecurityUserDetailsService implements UserDetailsService {

    private final io.roseiot.tenant.persistence.TenantMapper tenantMapper;

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserCredentialMapper credentialMapper;

    public SecurityUserDetailsService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            UserCredentialMapper credentialMapper,
            io.roseiot.tenant.persistence.TenantMapper tenantMapper) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.userRoleMapper = userRoleMapper;
        this.credentialMapper = credentialMapper;
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
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new UsernameNotFoundException(notFoundKey);
        }
        UserCredential credential = credentialMapper.selectById(user.getId());
        if (credential == null || credential.getPasswordHash() == null) {
            throw new UsernameNotFoundException(notFoundKey);
        }
        var tenant = user.getTenantId() == null ? null : tenantMapper.selectById(user.getTenantId());
        if (tenant == null || tenant.getDisabledAt() != null) throw new UsernameNotFoundException(notFoundKey);
        var authorities = userRoleMapper.listPermissionCodesForUser(user.getId()).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new SecurityUser(user, credential, authorities);
    }
}
