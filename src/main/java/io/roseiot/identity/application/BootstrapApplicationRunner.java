package io.roseiot.identity.application;

import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "true")
public class BootstrapApplicationRunner implements ApplicationRunner {

    private final Environment environment;
    private final BootstrapUserService bootstrapUserService;

    public BootstrapApplicationRunner(
            Environment environment, BootstrapUserService bootstrapUserService) {
        this.environment = environment;
        this.bootstrapUserService = bootstrapUserService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = environment.getProperty("BOOTSTRAP_USER_EMAIL");
        String password = environment.getProperty("BOOTSTRAP_USER_PASSWORD");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(
                    ApiErrorCode.BOOTSTRAP_VALIDATION,
                    "BOOTSTRAP_USER_EMAIL and BOOTSTRAP_USER_PASSWORD are required");
        }
        bootstrapUserService.bootstrap(email, password);
    }
}
