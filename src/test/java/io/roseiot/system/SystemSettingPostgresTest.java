package io.roseiot.system;

import io.roseiot.TestcontainersConfig;
import io.roseiot.system.SystemSettingType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
@WithMockUser(authorities = "system.setting.manage")
class SystemSettingPostgresTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SystemSettingService systemSettingService;

    @Test
    void createsByTypeAndUpdatesByIdWithoutTenantContext() {
        SystemSetting general = systemSettingService.create(SystemSettingType.GENERAL, json("""
                {"baseUrl":"https://roseiot.example","locale":"en"}
                """));
        assertThat(general.id()).isPositive();
        systemSettingService.create(SystemSettingType.SETUP, json("""
                {"complete":false}
                """));

        assertThat(systemSettingService.find(SystemSettingType.GENERAL).orElseThrow().setting().path("baseUrl").textValue())
                .isEqualTo("https://roseiot.example");
        assertThat(systemSettingService.find(SystemSettingType.SETUP).orElseThrow().setting())
                .isEqualTo(json("{\"complete\":false}"));

        systemSettingService.update(general.id(), json("{\"baseUrl\":\"https://new.example\"}"));
        assertThat(systemSettingService.requireGeneralBaseUrl()).isEqualTo("https://new.example");
        assertThat(systemSettingService.find(SystemSettingType.GENERAL).orElseThrow().setting().size()).isEqualTo(1);
    }

    private static JsonNode json(String value) {
        return JSON.readTree(value);
    }
}
