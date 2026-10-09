package io.roseiot.identity.infra.security;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import io.roseiot.shared.api.ApiErrorResponse;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JsonSecurityHandlersTest {

    private final JsonSecurityHandlers cut =
            new JsonSecurityHandlers(new ApiErrorBodyWriter(JsonMapper.builder().build()));

    private void assertBody(ResponseEntity<ApiErrorResponse> response, int status, String code, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiErrorResponse::code, ApiErrorResponse::message)
                .containsExactly(code, message);
    }

    @Test
    void shouldAnswerUnauthenticatedRequestWithJsonUnauthorized() throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cut.commence(new MockHttpServletRequest(), response, mock(AuthenticationException.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"code\":\"unauthorized\",\"message\":\"unauthorized\"}");
    }

    @Test
    void shouldAnswerAccessDeniedRequestWithJsonForbidden() throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cut.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"code\":\"forbidden\",\"message\":\"Forbidden\"}");
    }

    @Test
    void shouldAnswerAccessDeniedWithForbiddenOnMvcPath() {
        assertBody(cut.handleAccessDenied(new AccessDeniedException("denied")),
                403, "forbidden", "Forbidden");
    }

    @Test
    void shouldAnswerInvalidCredentialsWithUnauthorizedOnMvcPath() {
        assertBody(cut.handleAuthentication(AuthenticationExceptionMapping.invalidCredentials()),
                401, "invalid_credentials", "invalid_credentials");
    }
}
