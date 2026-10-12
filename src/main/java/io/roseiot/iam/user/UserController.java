package io.roseiot.iam.user;

import io.roseiot.shared.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
class UserController {

    private final UserService userService;
    private final UserCredentialService credentialService;
    UserController(UserService userService, UserCredentialService credentialService) {
        this.userService = userService;
        this.credentialService = credentialService;
    }

    @GetMapping("/me")
    User me(@AuthenticationPrincipal SecurityUser user) {
        return userService.getProfile(user.userId());
    }

    @PutMapping("/me")
    User updateMe(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody UpdateMeRequest request) {
        return userService.updateProfile(user.userId(), request.name());
    }

    @PutMapping("/me/password")
    void changePassword(
            @AuthenticationPrincipal SecurityUser user, @Valid @RequestBody ChangePasswordRequest request) {
        credentialService.changePassword(user.userId(), request.currentPassword(), request.newPassword());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('tenant.user.manage')")
    Page<UserResponse> list(
            @AuthenticationPrincipal SecurityUser user,
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Long tenantId = user.requireTenantId();
        return userService.list(tenantId, pageable);
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('tenant.user.manage')")
    UserResponse get(@AuthenticationPrincipal SecurityUser user, @PathVariable Long userId) {
        return userService.get(user.requireTenantId(), userId);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('tenant.user.manage')")
    UserResponse create(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody CreateUserRequest request) {
        return userService.create(user.requireTenantId(), request.email(), request.name(),
                request.password(), request.roleId());
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('tenant.user.manage')")
    void update(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        Long tenantId = user.requireTenantId();
        userService.update(tenantId, userId, request.disabled(), request.roleId());
    }

    @PostMapping("/password-resets")
    void requestPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        credentialService.requestResetByEmail(request.email());
    }

    @PostMapping("/password-resets/validations")
    ResponseEntity<ResetTokenValidationResponse> checkResetToken(
            @Valid @RequestBody ResetTokenRequest request) {
        ResetTokenValidationResponse response = new ResetTokenValidationResponse(
                credentialService.checkResetToken(request.token()).name().toLowerCase(Locale.ROOT));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/password-resets/confirmations")
    ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        credentialService.confirmPasswordReset(request.token(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }

    record UpdateMeRequest(@NotBlank @Size(max = 255) String name) {
    }
    record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(max = 72) String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[credentials=redacted]";
        }
    }
    record ResetPasswordRequest(@NotBlank String email) {
        @Override
        public String toString() {
            return "ResetPasswordRequest[email=redacted]";
        }
    }
    record UpdateUserRequest(@NotNull Boolean disabled, @NotNull Long roleId) {}
    record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 72) String password,
            @NotNull Long roleId) {
        @Override
        public String toString() {
            return "CreateUserRequest[personalDataAndCredentials=redacted, roleId=" + roleId + "]";
        }
    }
    record ResetTokenRequest(@NotBlank String token) {
        @Override
        public String toString() {
            return "ResetTokenRequest[token=redacted]";
        }
    }
    record ResetTokenValidationResponse(String status) {}
    record ConfirmPasswordResetRequest(
            @NotBlank String token,
            @NotBlank @Size(max = 72) String password) {
        @Override
        public String toString() {
            return "ConfirmPasswordResetRequest[credentials=redacted]";
        }
    }

}
