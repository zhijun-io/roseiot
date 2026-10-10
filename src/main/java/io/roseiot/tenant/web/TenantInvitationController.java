package io.roseiot.tenant.web;

import io.roseiot.iam.infra.security.MethodSecuritySpel;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.tenant.application.TenantInvitationService;
import io.roseiot.tenant.application.TenantInvitationService.InvitationSummary;
import io.roseiot.iam.application.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/invitations")
public class TenantInvitationController {

    private final TenantInvitationService invitationService;

    public TenantInvitationController(TenantInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    @PreAuthorize(MethodSecuritySpel.TENANT_INVITE_OR_MANAGE)
    List<InvitationResponse> list(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam(value = "status", required = false) String status) {
        return invitationService.list(user.requireTenantId(), status).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MethodSecuritySpel.TENANT_USER_INVITE)
    InvitationResponse create(
            @AuthenticationPrincipal SecurityUser user, @Valid @RequestBody CreateInvitationRequest request) {
        return InvitationResponse.from(invitationService.create(
                user.requireTenantId(), request.email(), request.roleId()));
    }

    @GetMapping("/{invitationId}")
    @PreAuthorize(MethodSecuritySpel.TENANT_INVITE_OR_MANAGE)
    InvitationResponse get(@AuthenticationPrincipal SecurityUser user, @PathVariable UUID invitationId) {
        return InvitationResponse.from(invitationService.get(user.requireTenantId(), invitationId));
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MethodSecuritySpel.TENANT_USER_INVITE)
    void revoke(@AuthenticationPrincipal SecurityUser user, @PathVariable UUID invitationId) {
        invitationService.revoke(user.requireTenantId(), invitationId);
    }

    public record CreateInvitationRequest(
            @NotBlank @Email String email,
            @jakarta.validation.constraints.NotNull UUID roleId) {}

    public record InvitationResponse(
            UUID id,
            String email,
            UUID roleId,
            Instant expiresAt,
            Instant createdAt,
            Instant acceptedAt,
            Instant revokedAt) {

        static InvitationResponse from(InvitationSummary summary) {
            return new InvitationResponse(
                    summary.id(),
                    summary.email(),
                    summary.roleId(),
                    summary.expiresAt(),
                    summary.createdAt(),
                    summary.acceptedAt(),
                    summary.revokedAt());
        }
    }
}
