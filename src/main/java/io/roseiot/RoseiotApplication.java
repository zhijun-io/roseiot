package io.roseiot;

import io.roseiot.platform.bootstrap.AlreadyBootstrappedException;
import io.roseiot.platform.bootstrap.BootstrapValidationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Arrays;
import java.util.Map;

@SpringBootApplication
public class RoseiotApplication {

    public static void main(String[] args) {
        boolean bootstrap = Arrays.asList(args).contains("--bootstrap");
        if (bootstrap) {
            String[] appArgs = Arrays.stream(args)
                    .filter(arg -> !"--bootstrap".equals(arg))
                    .toArray(String[]::new);
            System.exit(runBootstrapCommand(appArgs));
        }
        SpringApplication.run(RoseiotApplication.class, args);
    }

    public static int runBootstrapCommand(String... appArgs) {
        SpringApplication application = new SpringApplication(RoseiotApplication.class);
        application.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("roseiotBootstrapMode", Map.of(
                        "roseiot.bootstrap.enabled", "true",
                        "spring.main.web-application-type", "none"))));
        try {
            ConfigurableApplicationContext context = application.run(appArgs);
            return SpringApplication.exit(context);
        } catch (Throwable failure) {
            System.err.println(bootstrapFailureMessage(failure));
            return 1;
        }
    }

    static String bootstrapFailureMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof BootstrapValidationException || root instanceof AlreadyBootstrappedException) {
            return root.getMessage();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return "Bootstrap failed";
        }
        return message;
    }
}
