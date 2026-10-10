package io.roseiot.tenant.web;

import io.roseiot.iam.application.UserSessionService;
import io.roseiot.tenant.application.TenantInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/noauth/tenants/invitations")
public class TenantInvitationNoauthController {

    private final TenantInvitationService invitationService;

    public TenantInvitationNoauthController(TenantInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    TokenCheckResponse checkToken(@RequestParam("invitationToken") String invitationToken) {
        return TokenCheckResponse.from(invitationService.checkInvitationToken(invitationToken));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AcceptedInvitationResponse accept(@Valid @RequestBody AcceptInvitationRequest request) {
        UserSessionService.SessionTokens tokens =
                invitationService.acceptInvitation(request.invitationToken(), request.password());
        return new AcceptedInvitationResponse(tokens.accessToken(), tokens.refreshToken());
    }

    public record TokenCheckResponse(String status) {

        static TokenCheckResponse from(TenantInvitationService.InvitationTokenState state) {
            return new TokenCheckResponse(state.name().toLowerCase(Locale.ROOT));
        }
    }

    public record AcceptInvitationRequest(@NotBlank String invitationToken, @NotBlank String password) {}

    public record AcceptedInvitationResponse(String accessToken, String refreshToken) {}
}
