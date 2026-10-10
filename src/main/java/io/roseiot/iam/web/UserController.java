package io.roseiot.iam.web;

import io.roseiot.iam.application.UserService;
import io.roseiot.iam.infra.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    IdentityMeResponse updateMe(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody UpdateMeRequest request) {
        return IdentityMeResponse.from(userService.updateProfile(user.userId(), request.name()));
    }

    public record UpdateMeRequest(@NotBlank @Size(max = 255) String name) {}

    public record IdentityMeResponse(UUID id, String email, String name) {

        static IdentityMeResponse from(UserService.UserProfile profile) {
            return new IdentityMeResponse(profile.id(), profile.email(), profile.name());
        }
    }
}
