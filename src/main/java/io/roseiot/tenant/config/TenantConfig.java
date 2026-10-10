package io.roseiot.tenant.config;

import io.roseiot.system.application.SystemSettingService;
import io.roseiot.tenant.infra.notifier.LoggingTenantInvitationNotifier;
import io.roseiot.tenant.infra.notifier.TenantInvitationNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class TenantConfig {

    @Bean
    @Profile("local")
    @ConditionalOnMissingBean(TenantInvitationNotifier.class)
    TenantInvitationNotifier tenantInvitationNotifier(SystemSettingService settings) {
        return new LoggingTenantInvitationNotifier(settings);
    }
}
