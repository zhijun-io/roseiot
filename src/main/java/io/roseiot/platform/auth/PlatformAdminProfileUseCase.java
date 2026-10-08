package io.roseiot.platform.auth;

import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminProfileUseCase {

    private final PlatformAdminMapper platformAdminMapper;

    public PlatformAdminProfileUseCase(PlatformAdminMapper platformAdminMapper) {
        this.platformAdminMapper = platformAdminMapper;
    }

    public PlatformAdminProfile getProfile(UUID platformAdminId) {
        PlatformAdmin admin = platformAdminMapper.selectById(platformAdminId);
        if (admin == null) {
            throw new InvalidCredentialsException();
        }
        return new PlatformAdminProfile(admin.getId(), admin.getEmail());
    }

    public record PlatformAdminProfile(UUID id, String email) {}
}
