package io.roseiot.identity.bootstrap;

import io.roseiot.AbstractIt;
import io.roseiot.identity.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "roseiot.bootstrap.enabled=true")
class BootstrapCliTests extends AbstractIt {

    @DynamicPropertySource
    static void registerBootstrap(DynamicPropertyRegistry registry) {
        registry.add("BOOTSTRAP_USER_EMAIL", () -> "cli@example.com");
        registry.add("BOOTSTRAP_USER_PASSWORD", () -> "password1234");
    }

    @Autowired
    UserMapper userMapper;

    @Test
    void cliRunnerCreatesPlatformUserOnStartup() {
        assertThat(userMapper.selectCount(null)).isEqualTo(1);
        assertThat(userMapper.selectList(null).getFirst().getEmail()).isEqualTo("cli@example.com");
    }
}
