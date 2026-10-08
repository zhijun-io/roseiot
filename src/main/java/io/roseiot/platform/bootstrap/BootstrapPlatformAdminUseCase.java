package io.roseiot.platform.bootstrap;

import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.support.UuidV7;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class BootstrapPlatformAdminUseCase {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    private final PlatformAdminMapper platformAdminMapper;
    private final PasswordEncoder passwordEncoder;

    public BootstrapPlatformAdminUseCase(
            PlatformAdminMapper platformAdminMapper, PasswordEncoder passwordEncoder) {
        this.platformAdminMapper = platformAdminMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void bootstrap(String rawEmail, String rawPassword) {
        if (platformAdminMapper.selectCount(null) > 0) {
            throw new AlreadyBootstrappedException();
        }

        String email = normalizeEmail(rawEmail);
        validateEmail(email);
        validatePassword(rawPassword);

        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(UuidV7.generate());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setPasswordAlgorithm("bcrypt");
        admin.setCreatedAt(Instant.now());
        platformAdminMapper.insert(admin);
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

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12) {
            throw new BootstrapValidationException("Password must be at least 12 characters");
        }
        if (!HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find()) {
            throw new BootstrapValidationException("Password must contain letters and digits");
        }
    }
}
