package io.roseiot.tenant.infra.notifier;

import io.roseiot.system.application.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

public class LoggingTenantInvitationNotifier implements TenantInvitationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingTenantInvitationNotifier.class);

    private final SystemSettingService settings;

    public LoggingTenantInvitationNotifier(SystemSettingService settings) {
        this.settings = settings;
    }

    @Override
    public void sendInvitation(String email, String targetRole, String plainToken) {
        String link = UriComponentsBuilder.fromUriString(settings.requireGeneralBaseUrl())
                .path("/tenant-invitations/{token}")
                .encode()
                .buildAndExpand(plainToken)
                .toUriString();
        log.warn("Tenant notifier for role {} to {} — open link: {}", targetRole, email, link);
    }
}
