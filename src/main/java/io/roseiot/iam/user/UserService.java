package io.roseiot.iam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.mybatis.TenantContext;
import io.roseiot.iam.role.RoleService;
import io.roseiot.tenant.TenantService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserCredentialMapper credentialMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleService roleService;
    private final TenantService tenantService;
    private final UserCredentialService credentialService;
    private final Clock clock;

    public UserService(UserMapper userMapper, UserCredentialMapper credentialMapper, UserRoleMapper userRoleMapper,
                       RoleService roleService, TenantService tenantService, UserCredentialService credentialService,
                       Clock clock) {
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleService = roleService;
        this.tenantService = tenantService;
        this.credentialService = credentialService;
        this.clock = clock;
    }

    @PreAuthorize("isAuthenticated() and authentication.principal instanceof T(io.roseiot.shared.security.SecurityUser)"
            + " and #p0 == authentication.principal.userId()")
    public UserProfile getProfile(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) throw new BadCredentialsException(ApiErrorCode.INVALID_CREDENTIALS.code());
        return UserProfile.from(user);
    }

    @Transactional
    @PreAuthorize("isAuthenticated() and authentication.principal instanceof T(io.roseiot.shared.security.SecurityUser)"
            + " and #p0 == authentication.principal.userId()")
    public UserProfile updateProfile(Long userId, String name) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) throw new BadCredentialsException(ApiErrorCode.INVALID_CREDENTIALS.code());
        user.setName(name.trim());
        user.setUpdatedAt(Instant.now(clock));
        userMapper.updateById(user);
        return UserProfile.from(user);
    }

    Optional<UserAuthentication> findForAuthentication(String email) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        if (!isActive(user)) return Optional.empty();
        UserCredentialEntity credential = credentialMapper.selectById(user.getId());
        if (credential == null || credential.getPassword() == null) return Optional.empty();
        return Optional.of(authenticationData(user, credential.getPassword()));
    }

    Optional<UserAuthentication> findForAccessToken(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        return isActive(user) ? Optional.of(authenticationData(user, null)) : Optional.empty();
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

    private boolean emailExists(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT))) > 0;
    }

    @PreAuthorize("hasAuthority('tenant.user.manage')")
    public org.springframework.data.domain.Page<UserInfo> list(Long tenantId, Pageable pageable) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActive(tenantId);
        Pageable checkedPageable = checkedPageable(pageable);
        IPage<UserInfo> result = userRoleMapper.list(
                new Page<>(checkedPageable.getPageNumber() + 1L, checkedPageable.getPageSize()), tenantId);
        return new PageImpl<>(result.getRecords(), checkedPageable, result.getTotal());
    }

    private static Pageable checkedPageable(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100
                || !Sort.by(Sort.Order.asc("id")).equals(pageable.getSort())) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Use a page size from 1 to 100 and sort by id ascending");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
    }

    @PreAuthorize("hasAuthority('tenant.user.manage')")
    public UserInfo get(Long tenantId, Long userId) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActive(tenantId);
        UserInfo user = userRoleMapper.get(tenantId, userId);
        if (user == null) throw new BusinessException(ApiErrorCode.TENANT_USER_NOT_FOUND);
        return user;
    }

    @Transactional
    @PreAuthorize("hasAuthority('tenant.user.manage')")
    public UserInfo create(Long tenantId, String email, String name, String password, Long roleId) {
        tenantService.requireCustomerTenant(tenantId);
        tenantService.requireActiveForUpdate(tenantId);
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (emailExists(normalizedEmail)) {
            throw new BusinessException(ApiErrorCode.EMAIL_ALREADY_EXISTS);
        }
        roleService.requireAssignable(tenantId, roleId);

        Instant now = Instant.now(clock);
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
    @PreAuthorize("hasAuthority('tenant.user.manage')")
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
            Instant now = Instant.now(clock);
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
