package io.roseiot.platform.auth.api;

import io.roseiot.platform.auth.PlatformAdminRecoveryConfirmUseCase;
import io.roseiot.platform.auth.PlatformAdminRecoveryRequestUseCase;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/auth/recovery")
public class AuthRecoveryController {

    private final PlatformAdminRecoveryRequestUseCase recoveryRequestUseCase;
    private final PlatformAdminRecoveryConfirmUseCase recoveryConfirmUseCase;

    public AuthRecoveryController(
            PlatformAdminRecoveryRequestUseCase recoveryRequestUseCase,
            PlatformAdminRecoveryConfirmUseCase recoveryConfirmUseCase) {
        this.recoveryRequestUseCase = recoveryRequestUseCase;
        this.recoveryConfirmUseCase = recoveryConfirmUseCase;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RecoveryRequestResponse request(@Valid @RequestBody RecoveryRequest request) {
        String message = recoveryRequestUseCase.requestRecovery(request.email());
        return new RecoveryRequestResponse(message);
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@Valid @RequestBody RecoveryConfirmRequest request) {
        recoveryConfirmUseCase.confirmRecovery(request.token(), request.newPassword());
    }
}
