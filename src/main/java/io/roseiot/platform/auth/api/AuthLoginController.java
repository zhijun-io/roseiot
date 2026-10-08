package io.roseiot.platform.auth.api;

import io.roseiot.platform.auth.PlatformAdminLoginUseCase;
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

    public AuthLoginController(PlatformAdminLoginUseCase platformAdminLoginUseCase) {
        this.platformAdminLoginUseCase = platformAdminLoginUseCase;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        PlatformAdminLoginUseCase.LoginTokens tokens =
                platformAdminLoginUseCase.login(request.email(), request.password());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
