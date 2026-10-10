package io.roseiot.system.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.system.persistence.SystemSettingType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class SystemSettingPostgresTest {

    @Autowired
    private SystemSettingService settings;

    @Test
    void storesAndReplacesJsonSettingsWithoutTenantContext() {
        settings.replace(SystemSettingType.GENERAL, Map.of("baseUrl", "https://roseiot.example", "locale", "en"));
        settings.replace(SystemSettingType.SETUP, Map.of("complete", false));

        assertThat(settings.find(SystemSettingType.GENERAL).orElseThrow().getSetting())
                .containsEntry("baseUrl", "https://roseiot.example")
                .containsEntry("locale", "en");
        assertThat(settings.find(SystemSettingType.SETUP).orElseThrow().getSetting())
                .containsEntry("complete", false);

        settings.replace(SystemSettingType.GENERAL, Map.of("baseUrl", "https://new.example"));
        assertThat(settings.requireGeneralBaseUrl()).isEqualTo("https://new.example");
        assertThat(settings.find(SystemSettingType.GENERAL).orElseThrow().getSetting()).hasSize(1);
    }
}
