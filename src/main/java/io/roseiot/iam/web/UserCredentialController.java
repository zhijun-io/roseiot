package io.roseiot.iam.web;

import io.roseiot.iam.application.UserCredentialService;
import io.roseiot.iam.application.UserCredentialService.ResetTokenState;
import io.roseiot.iam.infra.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
public class UserCredentialController {

    private final UserCredentialService credentialService;

    public UserCredentialController(UserCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    void changePassword(
            @AuthenticationPrincipal SecurityUser user, @Valid @RequestBody ChangePasswordRequest request) {
        credentialService.changePassword(user.userId(), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.OK)
    void requestPasswordReset(@Valid @RequestBody ResetPasswordEmailRequest request) {
        credentialService.requestResetByEmail(request.email());
    }

    @GetMapping("/password-resets/{token}")
    TokenCheckResponse checkResetToken(@PathVariable("token") String resetToken) {
        return new TokenCheckResponse(
                credentialService.checkResetToken(resetToken).name().toLowerCase(Locale.ROOT));
    }

    @PutMapping("/password-resets/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(
            @PathVariable("token") String resetToken,
            @Valid @RequestBody ResetPasswordRequest request) {
        credentialService.confirmPasswordReset(resetToken, request.password());
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record ResetPasswordEmailRequest(@NotBlank String email) {}
    public record ResetPasswordRequest(@NotBlank String password) {}
    public record TokenCheckResponse(String status) {}
}
