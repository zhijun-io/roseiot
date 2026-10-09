package io.roseiot.identity.infra.security.session;

import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.infra.security.SecurityUserUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** {@code POST /api/logout} and {@code POST /api/logout-all} on the JWT filter chain. */
public final class SessionLogoutFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher LOGOUT =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/logout");

    private static final PathPatternRequestMatcher LOGOUT_ALL =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/logout-all");

    private final UserSessionService sessionService;

    public SessionLogoutFilter(UserSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (LOGOUT.matches(request) || LOGOUT_ALL.matches(request)) {
            var user = SecurityUserUtils.currentUser();
            if (user.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }
            if (LOGOUT.matches(request)) {
                sessionService.logoutCurrentSession(user.get().requireSessionId());
            } else {
                sessionService.logoutAllSessions(user.get().userId());
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
