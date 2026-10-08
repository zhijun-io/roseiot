package io.roseiot.platform.auth.api;

import io.roseiot.platform.auth.PlatformAdminLoginUseCase;
import io.roseiot.platform.auth.PlatformAdminRefreshUseCase;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/auth")
public class AuthLoginController {

    private final PlatformAdminLoginUseCase platformAdminLoginUseCase;
    private final PlatformAdminRefreshUseCase platformAdminRefreshUseCase;

    public AuthLoginController(
            PlatformAdminLoginUseCase platformAdminLoginUseCase,
            PlatformAdminRefreshUseCase platformAdminRefreshUseCase) {
        this.platformAdminLoginUseCase = platformAdminLoginUseCase;
        this.platformAdminRefreshUseCase = platformAdminRefreshUseCase;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        PlatformAdminLoginUseCase.LoginTokens tokens =
                platformAdminLoginUseCase.login(request.email(), request.password());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken());
    }

    @PostMapping("/token")
    LoginResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        PlatformAdminLoginUseCase.LoginTokens tokens =
                platformAdminRefreshUseCase.refresh(request.refreshToken());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
