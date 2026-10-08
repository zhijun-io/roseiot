package io.roseiot.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PlatformPasswordConfig {

    @Bean
    PasswordEncoder platformPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
