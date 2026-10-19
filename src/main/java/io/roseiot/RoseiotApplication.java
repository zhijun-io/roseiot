package io.roseiot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RoseiotApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoseiotApplication.class, args);
    }
}
