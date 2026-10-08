package io.roseiot.identity.infra.recovery;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class RecoveryNotifierTestConfig {

    @Bean
    InMemoryRecoveryNotifier recoveryNotifier() {
        return new InMemoryRecoveryNotifier();
    }
}
