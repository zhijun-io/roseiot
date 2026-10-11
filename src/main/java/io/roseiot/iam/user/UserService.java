package io.roseiot.iam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.mybatis.TenantContext;
import io.roseiot.shared.webmvc.CursorPage;
import io.roseiot.iam.role.RoleService;
import io.roseiot.tenant.TenantService;
import org.springframework.dao.DuplicateKeyException;
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
    private final UserCredentialService credentialService;

    public UserService(UserMapper userMapper, UserCredentialMapper credentialMapper, UserRoleMapper userRoleMapper,
                       RoleService roleService, TenantService tenantService, UserCredentialService credentialService) {
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleService = roleService;
        this.tenantService = tenantService;
        this.credentialService = credentialService;
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

    public CursorPage<UserInfo> list(Long tenantId, String cursor, int limit) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActive(tenantId);
        long afterId = CursorPage.afterId(cursor);
        int pageSize = CursorPage.checkedLimit(limit);
        List<UserInfo> rows = userRoleMapper.list(tenantId, afterId, pageSize + 1);
        return CursorPage.fromRows(rows, pageSize, UserInfo::id);
    }

    public UserInfo get(Long tenantId, Long userId) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActive(tenantId);
        UserInfo user = userRoleMapper.get(tenantId, userId);
        if (user == null) throw new BusinessException(ApiErrorCode.TENANT_USER_NOT_FOUND);
        return user;
    }

    @Transactional
    public UserInfo create(Long tenantId, String email, String name, String password, Long roleId) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActiveForUpdate(tenantId);
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (emailExists(normalizedEmail)) {
            throw new BusinessException(ApiErrorCode.EMAIL_ALREADY_EXISTS);
        }
        roleService.requireAssignable(tenantId, roleId);

        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setTenantId(tenantId);
        user.setEmail(normalizedEmail);
        user.setName(name.trim());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApiErrorCode.EMAIL_ALREADY_EXISTS, null, exception);
        }
        credentialService.createInitialPassword(user.getId(), password);
        userRoleMapper.insertAssignment(user.getId(), roleId, tenantId);
        roleService.ensureAdministrator(tenantId);
        return new UserInfo(user.getId(), user.getEmail(), user.getName(), roleId, false);
    }

    @Transactional
    public void update(Long tenantId, Long userId, Boolean disabled, Long roleId) {
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
