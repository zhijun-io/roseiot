package io.roseiot.identity.infra.security;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiErrorBodyWriter apiErrorBodyWriter;

    public JsonSecurityHandlers(ApiErrorBodyWriter apiErrorBodyWriter) {
        this.apiErrorBodyWriter = apiErrorBodyWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        apiErrorBodyWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "Unauthorized");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        apiErrorBodyWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden");
    }
}
