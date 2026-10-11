package io.roseiot.iam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.role.RoleService;
import io.roseiot.tenant.TenantService;




import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.mybatis.TenantContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserCredentialMapper credentialMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleService roleService;
    private final TenantService tenantService;

    public UserService(UserMapper userMapper, UserCredentialMapper credentialMapper, UserRoleMapper userRoleMapper,
                       RoleService roleService, TenantService tenantService) {
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleService = roleService;
        this.tenantService = tenantService;
    }

    public UserProfile getProfile(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) throw new BadCredentialsException(ApiErrorCode.INVALID_CREDENTIALS.code());
        return UserProfile.from(user);
    }

    @Transactional
    public UserProfile updateProfile(Long userId, String name) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) throw new BadCredentialsException(ApiErrorCode.INVALID_CREDENTIALS.code());
        user.setName(name.trim());
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);
        return UserProfile.from(user);
    }

    public UserAuthentication findForAuthentication(String email) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        if (!isActive(user)) return null;
        UserCredentialEntity credential = credentialMapper.selectById(user.getId());
        if (credential == null || credential.getPassword() == null) return null;
        return authenticationData(user, credential.getPassword());
    }

    public UserAuthentication findForAccessToken(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        return isActive(user) ? authenticationData(user, null) : null;
    }

    private UserAuthentication authenticationData(UserEntity user, String passwordHash) {
        List<String> permissions = TenantContext.call(user.getTenantId(),
                () -> userRoleMapper.listPermissionCodesForUser(user.getId()));
        User userModel = User.from(user);
        return new UserAuthentication(userModel, passwordHash, permissions);
    }

    private static boolean isActive(UserEntity user) {
        return user != null && user.getStatus() == UserStatus.ACTIVE;
    }

    public boolean emailExists(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT))) > 0;
    }

    public List<UserInfo> listTenantUsers(Long tenantId) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActive(tenantId);
        return userRoleMapper.listTenantUsers(tenantId);
    }

    @Transactional
    public void updateTenantUser(Long tenantId, Long userId, Boolean disabled, Long roleId) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActiveForUpdate(tenantId);
        if (disabled == null && roleId == null) throw new IllegalArgumentException("No changes requested");
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId())) {
            throw new BusinessException(ApiErrorCode.TENANT_USER_NOT_FOUND);
        }
        if (roleId != null) {
            roleService.requireAssignable(tenantId, roleId);
            userRoleMapper.delete(Wrappers.lambdaQuery(UserRoleEntity.class)
                    .eq(UserRoleEntity::getUserId, userId).eq(UserRoleEntity::getTenantId, tenantId));
            userRoleMapper.insertAssignment(userId, roleId, tenantId);
        }
        if (disabled != null) {
            Instant now = Instant.now();
            UserStatus status = disabled ? UserStatus.DISABLED : UserStatus.ACTIVE;
            userMapper.update(null, Wrappers.lambdaUpdate(UserEntity.class)
                    .eq(UserEntity::getId, userId)
                    .eq(UserEntity::getTenantId, tenantId)
                    .set(UserEntity::getStatus, status)
                    .set(UserEntity::getUpdatedAt, now));
        }
        roleService.ensureAdministrator(tenantId);
    }

}
