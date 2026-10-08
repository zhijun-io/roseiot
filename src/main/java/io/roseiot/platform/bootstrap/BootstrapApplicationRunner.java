package io.roseiot.platform.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "true")
public class BootstrapApplicationRunner implements ApplicationRunner {

    private final Environment environment;
    private final BootstrapPlatformAdminService bootstrapPlatformAdminService;

    public BootstrapApplicationRunner(
            Environment environment, BootstrapPlatformAdminService bootstrapPlatformAdminService) {
        this.environment = environment;
        this.bootstrapPlatformAdminService = bootstrapPlatformAdminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = environment.getProperty("BOOTSTRAP_ADMIN_EMAIL");
        String password = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new BootstrapValidationException("BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD are required");
        }
        bootstrapPlatformAdminService.bootstrap(email, password);
    }
}
