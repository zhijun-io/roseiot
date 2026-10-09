package io.roseiot.tenant.infra.invitation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingTenantInvitationNotifier implements TenantInvitationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingTenantInvitationNotifier.class);

    @Override
    public void sendInvitation(String email, String targetRole, String plainToken) {
        log.warn("Invitation for role {} requested for email {}", targetRole, email);
    }
}
