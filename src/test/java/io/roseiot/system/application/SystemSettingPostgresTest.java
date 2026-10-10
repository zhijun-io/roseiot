package io.roseiot.system.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.system.persistence.SystemSettingType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class SystemSettingPostgresTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SystemSettingService settings;

    @Test
    void storesAndReplacesJsonSettingsWithoutTenantContext() {
        settings.replace(SystemSettingType.GENERAL, json("""
                {"baseUrl":"https://roseiot.example","locale":"en"}
                """));
        settings.replace(SystemSettingType.SETUP, json("""
                {"complete":false}
                """));

        assertThat(settings.find(SystemSettingType.GENERAL).orElseThrow().getSetting().path("baseUrl").textValue())
                .isEqualTo("https://roseiot.example");
        assertThat(settings.find(SystemSettingType.SETUP).orElseThrow().getSetting())
                .isEqualTo(json("{\"complete\":false}"));

        settings.replace(SystemSettingType.GENERAL, json("{\"baseUrl\":\"https://new.example\"}"));
        assertThat(settings.requireGeneralBaseUrl()).isEqualTo("https://new.example");
        assertThat(settings.find(SystemSettingType.GENERAL).orElseThrow().getSetting().size()).isEqualTo(1);
    }

    private static JsonNode json(String value) {
        return JSON.readTree(value);
    }
}
