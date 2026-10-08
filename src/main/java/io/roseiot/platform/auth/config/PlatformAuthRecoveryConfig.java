package io.roseiot.platform.auth.config;

import io.roseiot.platform.auth.recovery.LoggingRecoveryNotificationAdapter;
import io.roseiot.platform.auth.recovery.RecoveryNotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAuthRecoveryConfig {

    @Bean
    @ConditionalOnMissingBean(RecoveryNotificationPort.class)
    RecoveryNotificationPort loggingRecoveryNotificationPort() {
        return new LoggingRecoveryNotificationAdapter();
    }
}
