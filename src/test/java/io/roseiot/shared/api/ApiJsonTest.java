package io.roseiot.shared.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiJsonTest {

    private final ApiJson cut = new ApiJson(JsonMapper.builder().build());

    @Test
    void shouldWriteStableJsonErrorBodyWithStatusAndCharset() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cut.writeError(response, 429, "rate_limited", "Too many requests");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString())
                .isEqualTo("{\"code\":\"rate_limited\",\"message\":\"Too many requests\"}");
    }

    @Test
    void shouldBuildResponseEntityWithSameErrorShape() {
        ResponseEntity<ApiErrorResponse> entity = cut.errorResponse(400, "bad_request", "Malformed request body");

        assertThat(entity.getStatusCode().value()).isEqualTo(400);
        assertThat(entity.getBody())
                .isNotNull()
                .extracting(ApiErrorResponse::code, ApiErrorResponse::message)
                .containsExactly("bad_request", "Malformed request body");
    }
}
