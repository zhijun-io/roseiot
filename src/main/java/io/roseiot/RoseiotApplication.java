package io.roseiot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

@SpringBootApplication
public class RoseiotApplication {

    public static void main(String[] args) {
        boolean bootstrap = Arrays.asList(args).contains("--bootstrap");
        if (bootstrap) {
            System.setProperty("roseiot.bootstrap.enabled", "true");
            System.setProperty("spring.main.web-application-type", "none");
            String[] appArgs = Arrays.stream(args)
                    .filter(arg -> !"--bootstrap".equals(arg))
                    .toArray(String[]::new);
            ConfigurableApplicationContext context =
                    SpringApplication.run(RoseiotApplication.class, appArgs);
            System.exit(SpringApplication.exit(context, () -> 0));
        }
        SpringApplication.run(RoseiotApplication.class, args);
    }
}
