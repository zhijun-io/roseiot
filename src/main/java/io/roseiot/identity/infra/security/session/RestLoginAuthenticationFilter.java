package io.roseiot.identity.infra.security.session;

import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.infra.security.SecurityExceptionHandler;
import io.roseiot.identity.infra.security.SecurityUser;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** {@code POST /api/login} — {@link AuthenticationManager} + session issuance. */
public final class RestLoginAuthenticationFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher LOGIN =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/login");

    private final AuthenticationManager authenticationManager;
    private final UserSessionService sessionService;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final JsonMapper jsonMapper;
    private final ApiErrorBodyWriter apiErrorBodyWriter;

    public RestLoginAuthenticationFilter(
            AuthenticationManager authenticationManager,
            UserSessionService sessionService,
            SecurityExceptionHandler securityExceptionHandler,
            JsonMapper jsonMapper,
            ApiErrorBodyWriter apiErrorBodyWriter) {
        this.authenticationManager = authenticationManager;
        this.sessionService = sessionService;
        this.securityExceptionHandler = securityExceptionHandler;
        this.jsonMapper = jsonMapper;
        this.apiErrorBodyWriter = apiErrorBodyWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!LOGIN.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        SessionFilterSupport.readAndValidate(
                request,
                response,
                jsonMapper,
                apiErrorBodyWriter,
                SessionFilterSupport.LoginBody.class,
                body -> !SessionFilterSupport.isBlank(body.email()) && !SessionFilterSupport.isBlank(body.password()),
                body -> authenticateAndIssueTokens(request, response, body));
    }

    private void authenticateAndIssueTokens(
            HttpServletRequest request, HttpServletResponse response, SessionFilterSupport.LoginBody body)
            throws IOException {
        String email = LoginEmails.normalize(body.email());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
            SecurityUser principal = (SecurityUser) authentication.getPrincipal();
            UserSessionService.SessionTokens tokens = sessionService.openSession(principal);
            SessionFilterSupport.writeTokens(jsonMapper, response, tokens.accessToken(), tokens.refreshToken());
        } catch (AuthenticationException exception) {
            securityExceptionHandler.commence(request, response, exception);
        }
    }
}
