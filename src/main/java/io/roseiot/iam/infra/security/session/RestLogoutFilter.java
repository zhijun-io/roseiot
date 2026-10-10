package io.roseiot.iam.infra.security.session;

import io.roseiot.iam.application.UserSessionService;
import io.roseiot.iam.infra.security.SecurityUserUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Handles authenticated session revocation endpoints in the Spring Security bearer chain. */
public final class RestLogoutFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher LOGOUT = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.DELETE, "/logout");

    private final UserSessionService sessionService;

    public RestLogoutFilter(UserSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!LOGOUT.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        var principal = SecurityUserUtils.currentUser();
        if (principal.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (Boolean.parseBoolean(request.getParameter("all"))) {
            sessionService.logoutAllSessions(principal.get().userId());
        } else {
            sessionService.logoutCurrentSession(principal.get().requireSessionId());
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
