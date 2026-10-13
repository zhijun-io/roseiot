package io.roseiot.shared.security;

import io.roseiot.shared.webmvc.ApiHttpSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;

/** Handles {@code POST /api/login}. */
public final class RestLoginAuthenticationFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher LOGIN =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/login");

    private final AuthenticationManager authenticationManager;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final ApiHttpSupport apiHttpSupport;
    private final JwtTokenFactory jwtTokenFactory;
    private final Clock clock;

    public RestLoginAuthenticationFilter(
            AuthenticationManager authenticationManager,
            JwtTokenFactory jwtTokenFactory,
            SecurityExceptionHandler securityExceptionHandler,
            ApiHttpSupport apiHttpSupport,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenFactory = jwtTokenFactory;
        this.securityExceptionHandler = securityExceptionHandler;
        this.apiHttpSupport = apiHttpSupport;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!LOGIN.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        LoginBody body;
        try {
            body = apiHttpSupport.readBody(request, LoginBody.class);
        } catch (JacksonException exception) {
            securityExceptionHandler.handleMalformedRequest(response);
            return;
        }
        if (body == null || !StringUtils.hasText(body.email()) || !StringUtils.hasText(body.password())) {
            apiHttpSupport.writeError(
                    response, HttpServletResponse.SC_BAD_REQUEST, "validation_error", "Validation failed");
            return;
        }

        try {
            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
            SecurityUser principal = (SecurityUser) result.getPrincipal();
            String token = jwtTokenFactory.createAccessToken(
                    principal.userId(), principal.requireTenantId(), Instant.now(clock));
            apiHttpSupport.writeJson(response, HttpServletResponse.SC_OK, new LoginResponse(token));
        } catch (AuthenticationException exception) {
            securityExceptionHandler.commence(request, response, exception);
        }
    }

    private record LoginBody(String email, String password) {

        @Override
        public String toString() {
            return "LoginBody[credentials=redacted]";
        }
    }

    private record LoginResponse(String accessToken) {

        @Override
        public String toString() {
            return "LoginResponse[accessToken=redacted]";
        }
    }
}
