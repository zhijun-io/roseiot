package io.roseiot.shared.security;

import io.roseiot.shared.webmvc.ApiHttpSupport;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityExceptionHandlerTest {

    private final SecurityExceptionHandler cut =
            new SecurityExceptionHandler(new ApiHttpSupport(JsonMapper.builder().build()));

    private void assertBody(ResponseEntity<ProblemDetail> response, int status, String code, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody())
                .isNotNull()
                .satisfies(problem -> {
                    assertThat(problem.getStatus()).isEqualTo(status);
                    assertThat(problem.getType().toString()).isEqualTo("urn:roseiot:problem:" + code);
                    assertThat(problem.getDetail()).isEqualTo(message);
                    assertThat(problem.getProperties()).containsEntry("code", code);
                });
    }

    @Test
    void shouldAnswerUnauthenticatedRequestWithJsonUnauthorized() throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cut.commence(new MockHttpServletRequest(), response, mock(AuthenticationException.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"urn:roseiot:problem:unauthorized\"")
                .contains("\"status\":401")
                .contains("\"code\":\"unauthorized\"");
    }

    @Test
    void shouldAnswerAccessDeniedRequestWithJsonForbidden() throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cut.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"urn:roseiot:problem:forbidden\"")
                .contains("\"status\":403")
                .contains("\"detail\":\"Forbidden\"");
    }

    @Test
    void shouldAnswerAccessDeniedWithForbiddenOnMvcPath() {
        assertBody(cut.handleAccessDenied(new AccessDeniedException("denied")),
                403, "forbidden", "Forbidden");
    }

    @Test
    void shouldAnswerInvalidCredentialsWithUnauthorizedOnMvcPath() {
        assertBody(cut.handleAuthentication(new BadCredentialsException("secret detail")),
                401, "invalid_credentials", "invalid_credentials");
    }

    @Test
    void shouldPreserveInvalidTokenCodeWithoutExposingDecoderDetails() {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_token", "signature and token details", null));

        assertBody(cut.handleAuthentication(exception), 401, "invalid_token", "invalid_token");
    }
}
