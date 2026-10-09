package io.roseiot.identity.infra.security;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
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

    @Test
    void shouldAnswerUnauthenticatedRequestWithJsonUnauthorized() throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cut.commence(new MockHttpServletRequest(), response, mock(AuthenticationException.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"code\":\"unauthorized\",\"message\":\"Unauthorized\"}");
    }

    @Test
    void shouldAnswerAccessDeniedRequestWithJsonForbidden() throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cut.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"code\":\"forbidden\",\"message\":\"Forbidden\"}");
    }
}
