package io.roseiot.iam.web;

import io.roseiot.iam.application.UserService;
import io.roseiot.iam.infra.security.SecurityUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    IdentityMeResponse me(@AuthenticationPrincipal SecurityUser user) {
        return IdentityMeResponse.from(userService.getProfile(user.userId()));
    }

    public record IdentityMeResponse(UUID id, String email) {

        static IdentityMeResponse from(UserService.UserProfile profile) {
            return new IdentityMeResponse(profile.id(), profile.email());
        }
    }
}
