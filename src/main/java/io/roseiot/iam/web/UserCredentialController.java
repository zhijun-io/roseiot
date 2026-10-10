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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
public class UserCredentialController {

    private final UserCredentialService credentialService;

    public UserCredentialController(UserCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PatchMapping("/api/user-credentials/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    void changePassword(
            @AuthenticationPrincipal SecurityUser user, @Valid @RequestBody ChangePasswordRequest request) {
        credentialService.changePassword(user.userId(), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/noauth/user-credentials/password-resets")
    @ResponseStatus(HttpStatus.OK)
    void requestPasswordReset(@Valid @RequestBody ResetPasswordEmailRequest request) {
        credentialService.requestResetByEmail(request.email());
    }

    @GetMapping("/noauth/user-credentials/password-resets")
    TokenCheckResponse checkResetToken(@RequestParam("resetToken") String resetToken) {
        return new TokenCheckResponse(credentialService.checkResetToken(resetToken).name().toLowerCase(Locale.ROOT));
    }

    @PutMapping("/noauth/user-credentials/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        credentialService.confirmPasswordReset(request.resetToken(), request.password());
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record ResetPasswordEmailRequest(@NotBlank String email) {}
    public record ResetPasswordRequest(@NotBlank String resetToken, @NotBlank String password) {}
    public record TokenCheckResponse(String status) {}
}
