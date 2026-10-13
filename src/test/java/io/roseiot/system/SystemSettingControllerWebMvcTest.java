package io.roseiot.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import io.roseiot.iam.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.iam.security.RoseiotWebMvcTestSupport;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.security.JwtAuthenticationConverter;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = SystemSettingController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class SystemSettingControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SystemSettingService systemSettingService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldCreateSettingForPlatformAdministrator() throws Exception {
        var setting = JsonMapper.builder().build().readTree("{\"baseUrl\":\"https://roseiot.example\"}");
        given(systemSettingService.create(SystemSettingType.GENERAL, setting))
                .willReturn(new SystemSetting(3L, SystemSettingType.GENERAL, setting, Instant.EPOCH, Instant.EPOCH));

        assertThat(mvc.post()
                        .uri("/api/system-settings")
                        .with(RoseiotWebMvcTestSupport.jwtWithAuthorities(
                                this::systemToken, RoseiotWebMvcTestSupport.action("system.setting.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GENERAL\",\"setting\":{\"baseUrl\":\"https://roseiot.example\"}}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("GENERAL");

        verify(systemSettingService).create(SystemSettingType.GENERAL, setting);
    }

    @Test
    void shouldDenySettingsToTenantRole() {
        assertThat(mvc.get()
                        .uri("/api/system-settings")
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::systemToken)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturnNotFoundForMissingSetting() {
        willThrow(new BusinessException(ApiErrorCode.SYSTEM_SETTING_NOT_FOUND))
                .given(systemSettingService)
                .get(999L);

        assertThat(mvc.get()
                        .uri("/api/system-settings/999")
                        .with(RoseiotWebMvcTestSupport.jwtWithAuthorities(
                                this::systemToken, RoseiotWebMvcTestSupport.action("system.setting.manage"))))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("system_setting_not_found");
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none").subject("1001").issuedAt(now).expiresAt(now.plusSeconds(60));
    }
}
