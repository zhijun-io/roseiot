package io.roseiot.tenant.infra.invitation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

public class LoggingTenantInvitationNotifier implements TenantInvitationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingTenantInvitationNotifier.class);

    private final String linkBaseUrl;

    public LoggingTenantInvitationNotifier(String linkBaseUrl) {
        this.linkBaseUrl = linkBaseUrl;
    }

    @Override
    public void sendInvitation(String email, String targetRole, String plainToken) {
        String link = UriComponentsBuilder.fromUriString(linkBaseUrl)
                .path("/api/noauth/tenant-invitations")
                .queryParam("invitationToken", plainToken)
                .build()
                .toUriString();
        log.warn("Tenant invitation for role {} to {} — open link: {}", targetRole, email, link);
    }
}
