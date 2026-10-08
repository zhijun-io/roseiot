package io.roseiot.platform.auth.recovery;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class RecoveryNotificationTestConfig {

    @Bean
    InMemoryRecoveryNotificationAdapter recoveryNotificationPort() {
        return new InMemoryRecoveryNotificationAdapter();
    }
}
