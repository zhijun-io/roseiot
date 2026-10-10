package io.roseiot.tenant.config;

import io.roseiot.tenant.infra.invitation.LoggingTenantInvitationNotifier;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class TenantConfig {

    @Bean
    @Profile("local")
    @ConditionalOnMissingBean(TenantInvitationNotifier.class)
    TenantInvitationNotifier tenantInvitationNotifier(
            @Value("${roseiot.auth.tenant-invitation.link-base-url}") String invitationLinkBaseUrl) {
        return new LoggingTenantInvitationNotifier(invitationLinkBaseUrl);
    }
}
