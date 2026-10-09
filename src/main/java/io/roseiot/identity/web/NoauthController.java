package io.roseiot.identity.web;

import io.roseiot.identity.application.PasswordResetService;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.application.TenantInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/noauth")
public class NoauthController {

    private final PasswordResetService passwordResetService;
    private final TenantInvitationService tenantInvitationService;

    public NoauthController(
            PasswordResetService passwordResetService, TenantInvitationService tenantInvitationService) {
        this.passwordResetService = passwordResetService;
        this.tenantInvitationService = tenantInvitationService;
    }

    @PostMapping("/password-reset/email")
    @ResponseStatus(HttpStatus.OK)
    void resetPasswordByEmail(@Valid @RequestBody ResetPasswordEmailRequest request) {
        passwordResetService.requestResetByEmail(request.email());
    }

    @GetMapping("/password-reset")
    TokenCheckResponse checkResetToken(@RequestParam("resetToken") String resetToken) {
        return TokenCheckResponse.from(passwordResetService.checkResetToken(resetToken));
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.confirmPasswordReset(request.resetToken(), request.password());
    }

    @GetMapping("/user-activation")
    void checkActivateToken(@RequestParam("activateToken") String activateToken) {
        throw new BusinessException(ApiErrorCode.NOT_IMPLEMENTED);
    }

    @PostMapping("/user-activation")
    SessionTokenResponse activateUser(@Valid @RequestBody ActivateUserRequest request) {
        throw new BusinessException(ApiErrorCode.NOT_IMPLEMENTED);
    }

    @GetMapping("/tenant-invitations")
    TokenCheckResponse checkInvitationToken(@RequestParam("invitationToken") String invitationToken) {
        return TokenCheckResponse.from(tenantInvitationService.checkInvitationToken(invitationToken));
    }

    @PostMapping("/tenant-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    SessionTokenResponse acceptTenantInvitation(
            @Valid @RequestBody AcceptTenantInvitationRequest request) {
        return SessionTokenResponse.from(tenantInvitationService.acceptInvitation(
                request.invitationToken(), request.password()));
    }

    public record TokenCheckResponse(String status) {

        static TokenCheckResponse from(Enum<?> state) {
            return new TokenCheckResponse(state.name().toLowerCase());
        }
    }

    public record ResetPasswordEmailRequest(@NotBlank String email) {}

    public record ResetPasswordRequest(@NotBlank String resetToken, @NotBlank String password) {}

    public record AcceptTenantInvitationRequest(@NotBlank String invitationToken, @NotBlank String password) {}

    public record ActivateUserRequest(@NotBlank String activateToken, @NotBlank String password) {}
}
