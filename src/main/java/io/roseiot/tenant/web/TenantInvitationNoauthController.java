package io.roseiot.tenant.web;

import io.roseiot.tenant.application.TenantInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/tenant-invitations")
public class TenantInvitationNoauthController {

    private final TenantInvitationService invitationService;

    public TenantInvitationNoauthController(TenantInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/{token}")
    TokenCheckResponse checkToken(@PathVariable("token") String invitationToken) {
        return TokenCheckResponse.from(invitationService.checkInvitationToken(invitationToken));
    }

    @PutMapping("/{token}")
    AcceptedInvitationResponse accept(
            @PathVariable("token") String invitationToken,
            @Valid @RequestBody AcceptInvitationRequest request) {
        String accessToken = invitationService.acceptInvitation(invitationToken, request.password());
        return new AcceptedInvitationResponse(accessToken);
    }

    public record TokenCheckResponse(String status) {

        static TokenCheckResponse from(TenantInvitationService.InvitationTokenState state) {
            return new TokenCheckResponse(state.name().toLowerCase(Locale.ROOT));
        }
    }

    public record AcceptInvitationRequest(@NotBlank String password) {}

    public record AcceptedInvitationResponse(String accessToken) {}
}
