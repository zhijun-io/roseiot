package io.roseiot;

import org.springframework.boot.SpringApplication;

public class TestRoseiotApplication {

    public static void main(String[] args) {
        SpringApplication.from(RoseiotApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
