package io.roseiot.platform.bootstrap;

import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.AbstractIt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "roseiot.bootstrap.enabled=true")
class PlatformBootstrapCliTests extends AbstractIt {

    @DynamicPropertySource
    static void registerBootstrap(DynamicPropertyRegistry registry) {
        registry.add("BOOTSTRAP_ADMIN_EMAIL", () -> "cli@example.com");
        registry.add("BOOTSTRAP_ADMIN_PASSWORD", () -> "password1234");
    }

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @Test
    void cliRunnerCreatesPlatformAdminOnStartup() {
        assertThat(platformAdminMapper.selectCount(null)).isEqualTo(1);
        assertThat(platformAdminMapper.selectList(null).getFirst().getEmail()).isEqualTo("cli@example.com");
    }
}
