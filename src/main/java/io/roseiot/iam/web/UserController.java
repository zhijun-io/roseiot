package io.roseiot.iam.web;

import io.roseiot.iam.application.UserService;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/me")
    @PreAuthorize("isAuthenticated()")
    IdentityMeResponse me(@AuthenticationPrincipal SecurityUser user) {
        return IdentityMeResponse.from(userService.getProfile(user.userId()));
    }

    @GetMapping("/noauth/users/activation")
    void checkActivateToken(@RequestParam("activateToken") String activateToken) {
        throw new BusinessException(ApiErrorCode.NOT_IMPLEMENTED);
    }

    @PostMapping("/noauth/users/activation")
    void activateUser(@Valid @RequestBody ActivateUserRequest request) {
        throw new BusinessException(ApiErrorCode.NOT_IMPLEMENTED);
    }

    public record IdentityMeResponse(UUID id, String email) {

        static IdentityMeResponse from(UserService.UserProfile profile) {
            return new IdentityMeResponse(profile.id(), profile.email());
        }
    }

    public record ActivateUserRequest(@NotBlank String activateToken, @NotBlank String password) {}
}
