package io.roseiot.identity.web;

import io.roseiot.identity.application.PasswordResetService;
import io.roseiot.identity.domain.ActivationNotImplementedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/noauth")
public class NoauthController {

    private static final String PASSWORD_RESET_FORM_PATH = "/auth/password-reset";

    private static final String PASSWORD_RESET_LINK_EXPIRED_PATH = "/auth/password-reset/expired";

    private final PasswordResetService passwordResetService;

    public NoauthController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/password-reset/email")
    @ResponseStatus(HttpStatus.OK)
    void resetPasswordByEmail(@Valid @RequestBody ResetPasswordEmailRequest request) {
        passwordResetService.requestResetByEmail(request.email());
    }

    @GetMapping("/password-reset")
    ResponseEntity<Void> checkResetToken(@RequestParam("resetToken") String resetToken) {
        return switch (passwordResetService.checkResetToken(resetToken)) {
            case VALID -> seeOther(resetFormLocation(resetToken));
            case EXPIRED -> seeOther(PASSWORD_RESET_LINK_EXPIRED_PATH);
            case INVALID -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.confirmPasswordReset(request.resetToken(), request.password());
    }

    @GetMapping("/user-activation")
    ResponseEntity<Void> checkActivateToken(@RequestParam("activateToken") String activateToken) {
        throw new ActivationNotImplementedException();
    }

    @PostMapping("/user-activation")
    LoginResponse activateUser(@Valid @RequestBody ActivateUserRequest request) {
        throw new ActivationNotImplementedException();
    }

    private ResponseEntity<Void> seeOther(URI location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }

    private ResponseEntity<Void> seeOther(String path) {
        return seeOther(UriComponentsBuilder.fromPath(path).build().toUri());
    }

    private URI resetFormLocation(String resetToken) {
        return UriComponentsBuilder.fromPath(PASSWORD_RESET_FORM_PATH)
                .queryParam("resetToken", resetToken)
                .build()
                .toUri();
    }

    public record ResetPasswordEmailRequest(@NotBlank String email) {
    }

    public record ResetPasswordRequest(@NotBlank String resetToken, @NotBlank String password) {
    }

    public record ActivateUserRequest(@NotBlank String activateToken, @NotBlank String password) {
    }

    /**
     * Placeholder for future {@code POST /api/noauth/user-activation} token pair.
     */
    public record LoginResponse(String token, String refreshToken) {
    }
}
