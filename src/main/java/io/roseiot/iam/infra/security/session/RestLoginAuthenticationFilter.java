package io.roseiot.iam.infra.security.session;

import io.roseiot.iam.application.UserSessionService;
import io.roseiot.iam.domain.LoginEmails;
import io.roseiot.iam.infra.security.SecurityExceptionHandler;
import io.roseiot.iam.infra.security.SecurityUser;
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

/** Handles {@code POST /login}. */
public final class RestLoginAuthenticationFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher LOGIN = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/login");

    private final AuthenticationManager authenticationManager;
    private final UserSessionService sessionService;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final JsonMapper jsonMapper;
    private final ApiErrorBodyWriter errorWriter;

    public RestLoginAuthenticationFilter(
            AuthenticationManager authenticationManager,
            UserSessionService sessionService,
            SecurityExceptionHandler securityExceptionHandler,
            JsonMapper jsonMapper,
            ApiErrorBodyWriter errorWriter) {
        this.authenticationManager = authenticationManager;
        this.sessionService = sessionService;
        this.securityExceptionHandler = securityExceptionHandler;
        this.jsonMapper = jsonMapper;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!LOGIN.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        SessionFilterSupport.readAndValidate(request, response, jsonMapper, errorWriter,
                SessionFilterSupport.LoginBody.class,
                body -> !SessionFilterSupport.isBlank(body.email()) && !SessionFilterSupport.isBlank(body.password()),
                body -> authenticate(request, response, body));
    }

    private void authenticate(
            HttpServletRequest request, HttpServletResponse response, SessionFilterSupport.LoginBody body)
            throws IOException {
        try {
            Authentication result = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    LoginEmails.normalize(body.email()), body.password()));
            SecurityUser principal = (SecurityUser) result.getPrincipal();
            UserSessionService.SessionTokens tokens = sessionService.openSession(principal);
            SessionFilterSupport.writeTokens(jsonMapper, response, tokens.accessToken(), tokens.refreshToken());
        } catch (AuthenticationException exception) {
            securityExceptionHandler.commence(request, response, exception);
        }
    }
}
