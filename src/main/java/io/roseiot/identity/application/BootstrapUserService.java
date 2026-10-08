package io.roseiot.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.domain.AlreadyBootstrappedException;
import io.roseiot.identity.domain.BootstrapValidationException;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.domain.PasswordPolicy;
import io.roseiot.shared.util.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public BootstrapUserService(
            UserMapper userMapper, JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void bootstrap(String rawEmail, String rawPassword) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().isNull(User::getTenantId)) > 0) {
            throw new AlreadyBootstrappedException();
        }

        String email = normalizeEmail(rawEmail);
        validateEmail(email);
        PasswordPolicy.validationError(rawPassword)
                .ifPresent(message -> {
                    throw new BootstrapValidationException(message);
                });

        UUID userId = UuidV7.generate();
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordAlgorithm("bcrypt");
        user.setAuthorizationVersion(1);
        user.setCreatedAt(Instant.now());
        userMapper.insert(user);

        UUID platformOperatorRoleId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM "role" WHERE code = ?
                """,
                UUID.class,
                "PlatformOperator");

        jdbcTemplate.update(
                """
                INSERT INTO user_role (id, user_id, role_id, scope_tenant_id)
                VALUES (?, ?, ?, NULL)
                """,
                UuidV7.generate(),
                userId,
                platformOperatorRoleId);
    }

    static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            throw new BootstrapValidationException("Email is required");
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BootstrapValidationException("Invalid email");
        }
    }
}
