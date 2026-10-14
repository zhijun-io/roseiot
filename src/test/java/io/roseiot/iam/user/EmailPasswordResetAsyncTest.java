package io.roseiot.iam.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.roseiot.config.AsyncConfig;
import io.roseiot.system.SystemSettingService;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class EmailPasswordResetAsyncTest {

    @Test
    void dispatchesCommittedNotificationOnTheNamedAsyncExecutor() {
        new ApplicationContextRunner()
                .withUserConfiguration(AsyncConfiguration.class)
                .withPropertyValues(
                        "roseiot.password-reset.email.enabled=true",
                        "roseiot.password-reset.email.from=no-reply@roseiot.example")
                .run(context -> {
                    JavaMailSender mailSender = context.getBean(JavaMailSender.class);
                    SystemSettingService settings = context.getBean(SystemSettingService.class);
                    when(settings.requireGeneralBaseUrl()).thenReturn("https://roseiot.example");

                    var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                    transaction.executeWithoutResult(status -> context.publishEvent(
                            new PasswordResetRequestedEvent("person@example.com", "one-time-token")));

                    Awaitility.await()
                            .atMost(Duration.ofSeconds(3))
                            .untilAsserted(() -> verify(mailSender).send(any(SimpleMailMessage.class)));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({AsyncConfig.class, EmailPasswordResetNotifier.class})
    static class AsyncConfiguration {

        @Bean
        JavaMailSender javaMailSender() {
            return mock(JavaMailSender.class);
        }

        @Bean
        SystemSettingService systemSettingService() {
            return mock(SystemSettingService.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
