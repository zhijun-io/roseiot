package io.roseiot.auth;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class AuthController {
    private final AuthenticationService authentication;
    private final PasswordResetService passwordResets;

    AuthController(AuthenticationService authentication, PasswordResetService passwordResets) {
        this.authentication = authentication;
        this.passwordResets = passwordResets;
    }

    @PostMapping("/auth/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return noStore().body(authentication.login(request));
    }

    @PostMapping("/auth/password-reset-requests")
    ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResets.request(request.email());
        return noStore().body(new MessageResponse("If the account exists, password reset instructions will be sent."));
    }

    @PostMapping("/auth/password-resets")
    ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody PasswordResetCompletionRequest request) {
        passwordResets.complete(request.token(), request.newPassword());
        return noStore().body(new MessageResponse("Password has been reset."));
    }

    @PutMapping("/me/password")
    ResponseEntity<PasswordChangeResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest request) {
        var response = authentication.changePassword(
                Long.parseLong(jwt.getSubject()), Long.parseLong(jwt.getClaimAsString("sid")), request);
        return noStore().body(response);
    }

    private static ResponseEntity.BodyBuilder noStore() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache");
    }
}
