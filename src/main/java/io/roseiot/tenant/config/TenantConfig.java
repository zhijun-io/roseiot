package io.roseiot.tenant.config;

import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import io.roseiot.tenant.infra.invitation.LoggingTenantInvitationNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantConfig {

    @Bean
    @ConditionalOnMissingBean(TenantInvitationNotifier.class)
    TenantInvitationNotifier tenantInvitationNotifier() {
        return new LoggingTenantInvitationNotifier();
    }
}
