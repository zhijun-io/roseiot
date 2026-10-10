package io.roseiot.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.domain.PasswordPolicy;
import io.roseiot.identity.persistence.RoleMapper;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRoleMapper;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.shared.util.UuidV7;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class BootstrapUserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public BootstrapUserService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void bootstrap(String rawEmail, String rawPassword) {
        if (userMapper.countUnscopedUsers() > 0) {
            throw new BusinessException(
                    ApiErrorCode.ALREADY_BOOTSTRAPPED, "System is already bootstrapped");
        }

        String email = normalizeEmail(rawEmail);
        validateEmail(email);
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException(ApiErrorCode.BOOTSTRAP_VALIDATION, "Email already exists");
        }
        PasswordPolicy.ensureValid(rawPassword, ApiErrorCode.BOOTSTRAP_VALIDATION);

        UUID userId = UuidV7.generate();
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordAlgorithm("bcrypt");
        user.setAuthorizationVersion(1);
        user.setCreatedAt(Instant.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // The database constraint closes the race between concurrent bootstrap requests.
            throw new BusinessException(
                    ApiErrorCode.ALREADY_BOOTSTRAPPED, "System is already bootstrapped");
        }

        userRoleMapper.insertAssignment(
                UuidV7.generate(), userId, roleMapper.requireIdByCode(RoleService.SYSTEM_ADMIN), null);
    }

    static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            throw new BusinessException(ApiErrorCode.BOOTSTRAP_VALIDATION, "Email is required");
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ApiErrorCode.BOOTSTRAP_VALIDATION, "Invalid email");
        }
    }
}
