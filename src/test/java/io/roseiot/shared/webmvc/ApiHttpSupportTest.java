package io.roseiot.shared.webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ApiHttpSupportTest {

    private final ApiHttpSupport cut = new ApiHttpSupport(JsonMapper.builder().build());

    @Test
    void shouldWriteRfc9457ProblemWithStatusAndCharset() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cut.writeError(response, 429, "rate_limited", "Too many requests");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString())
                .contains("\"type\":\"urn:roseiot:problem:rate_limited\"")
                .contains("\"title\":\"Too Many Requests\"")
                .contains("\"status\":429")
                .contains("\"detail\":\"Too many requests\"")
                .contains("\"code\":\"rate_limited\"")
                .doesNotContain("\"properties\"");
    }

    @Test
    void shouldBuildProblemDetailResponseEntity() {
        ResponseEntity<ProblemDetail> entity = cut.errorResponse(400, "bad_request", "Malformed request body");

        assertThat(entity.getStatusCode().value()).isEqualTo(400);
        assertThat(entity.getBody()).isNotNull().satisfies(problem -> {
            assertThat(problem.getType().toString()).isEqualTo("urn:roseiot:problem:bad_request");
            assertThat(problem.getTitle()).isEqualTo("Bad Request");
            assertThat(problem.getDetail()).isEqualTo("Malformed request body");
            assertThat(problem.getProperties()).containsEntry("code", "bad_request");
        });
        assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(entity.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
