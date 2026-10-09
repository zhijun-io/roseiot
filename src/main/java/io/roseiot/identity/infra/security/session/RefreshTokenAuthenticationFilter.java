package io.roseiot.identity.infra.security.session;

import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.infra.security.SecurityExceptionHandler;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** {@code POST /api/token} — refresh-token rotation. */
public final class RefreshTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher REFRESH =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/token");

    private final UserSessionService sessionService;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final JsonMapper jsonMapper;
    private final ApiErrorBodyWriter apiErrorBodyWriter;

    public RefreshTokenAuthenticationFilter(
            UserSessionService sessionService,
            SecurityExceptionHandler securityExceptionHandler,
            JsonMapper jsonMapper,
            ApiErrorBodyWriter apiErrorBodyWriter) {
        this.sessionService = sessionService;
        this.securityExceptionHandler = securityExceptionHandler;
        this.jsonMapper = jsonMapper;
        this.apiErrorBodyWriter = apiErrorBodyWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!REFRESH.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        SessionFilterSupport.readAndValidate(
                request,
                response,
                jsonMapper,
                apiErrorBodyWriter,
                SessionFilterSupport.RefreshBody.class,
                body -> !SessionFilterSupport.isBlank(body.refreshToken()),
                body -> refreshAndWriteTokens(request, response, body));
    }

    private void refreshAndWriteTokens(
            HttpServletRequest request, HttpServletResponse response, SessionFilterSupport.RefreshBody body)
            throws IOException {
        try {
            UserSessionService.SessionTokens tokens = sessionService.refresh(body.refreshToken());
            SessionFilterSupport.writeTokens(jsonMapper, response, tokens.accessToken(), tokens.refreshToken());
        } catch (AuthenticationException exception) {
            securityExceptionHandler.commence(request, response, exception);
        }
    }
}
