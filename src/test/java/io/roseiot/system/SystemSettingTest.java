package io.roseiot.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SystemSettingTest {

    @Test
    void doesNotExposeSettingValuesInToString() {
        var setting = new SystemSetting(
                1L,
                SystemSettingType.GENERAL,
                JsonMapper.builder().build().readTree("{\"secret\":\"sensitive-value\"}"),
                Instant.EPOCH,
                Instant.EPOCH);

        assertThat(setting.toString()).doesNotContain("sensitive-value");

        var request = new SystemSettingController.CreateSystemSettingRequest(
                SystemSettingType.GENERAL, JsonMapper.builder().build().readTree("{\"secret\":\"sensitive-value\"}"));
        assertThat(request.toString()).doesNotContain("sensitive-value");
    }
}
