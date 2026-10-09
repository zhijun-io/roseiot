package io.roseiot.shared.ratelimit;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.WriteListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final byte[] LOGIN_BODY = """
            {"email":"ops@roseiot.io","password":"correct horse 42"}
            """.getBytes(StandardCharsets.UTF_8);

    private HttpServletRequest request;
    private HttpServletResponse response;
    private RecordingOutputStream responseBody;
    private FilterChain filterChain;
    private RateLimitProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new RecordingOutputStream();
        filterChain = mock(FilterChain.class);
        when(response.getOutputStream()).thenReturn(responseBody);
        properties = new RateLimitProperties();
        properties.setWindowMinutes(60);
        properties.setRules(List.of(loginRule(1)));
    }

    private RateLimitProperties.Rule loginRule(int maxAttempts) {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setMethod("POST");
        rule.setPath(LOGIN_PATH);
        rule.setBodyField("email");
        rule.setKeyPrefix("auth:login:");
        rule.setMaxAttempts(maxAttempts);
        return rule;
    }

    private RateLimitFilter cut(SlidingWindowRateLimiter rateLimiter) {
        return new RateLimitFilter(rateLimiter, properties, JsonMapper.builder().build(),
                new ApiErrorBodyWriter(JsonMapper.builder().build()));
    }

    private void stubLoginRequest() throws IOException {
        stubRequest("POST", LOGIN_PATH, LOGIN_BODY);
    }

    /** Each read of the body must hand out a fresh stream, the way separate requests do. */
    private void stubRequest(String method, String path, byte[] body) throws IOException {
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getInputStream()).thenAnswer(invocation -> new StubInputStream(body));
    }

    @Test
    void shouldPassRequestThroughWhenNoRuleMatches() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/identity/me");
        RateLimitFilter cut = cut(new SlidingWindowRateLimiter(properties));

        cut.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(responseBody.asText()).isEmpty();
    }

    @Test
    void shouldHandDownstreamAReplayableBodyAfterReadingItForTheKey() throws Exception {
        stubLoginRequest();
        RateLimitFilter cut = cut(new SlidingWindowRateLimiter(properties));

        cut.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> forwarded = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(forwarded.capture(), any(HttpServletResponse.class));
        assertThat(forwarded.getValue().getInputStream().readAllBytes()).isEqualTo(LOGIN_BODY);
        assertThat(forwarded.getValue()).isNotSameAs(request);
    }

    @Test
    void shouldAllowFirstAttemptWithinTheLimit() throws Exception {
        stubLoginRequest();
        RateLimitFilter cut = cut(new SlidingWindowRateLimiter(properties));

        cut.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(responseBody.asText()).isEmpty();
    }

    @Test
    void shouldRejectSecondAttemptBeyondTheLimitWithStableErrorBody() throws Exception {
        stubLoginRequest();
        SlidingWindowRateLimiter rateLimiter = new SlidingWindowRateLimiter(properties);
        RateLimitFilter cut = cut(rateLimiter);
        cut.doFilterInternal(request, response, filterChain);

        // The first attempt is legitimately served; only the second one must be refused.
        clearInvocations(filterChain, response);
        cut.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        verify(filterChain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(responseBody.asText())
                .contains("\"code\":\"rate_limited\"")
                .contains("\"message\":\"Too many requests\"");
    }

    @Test
    void shouldNotCountRequestsWhoseRateLimitKeyIsMissing() throws Exception {
        stubRequest("POST", LOGIN_PATH, """
                {"password":"correct horse 42"}
                """.getBytes(StandardCharsets.UTF_8));
        RateLimitFilter cut = cut(new SlidingWindowRateLimiter(properties));

        cut.doFilterInternal(request, response, filterChain);
        cut.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(2))
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(responseBody.asText()).isEmpty();
    }

    @Test
    void shouldNotLimitAnyAttemptWhenRateLimitingIsDisabled() throws Exception {
        properties.setEnabled(false);
        stubLoginRequest();
        RateLimitFilter cut = cut(new SlidingWindowRateLimiter(properties));

        for (int attempt = 0; attempt < 3; attempt++) {
            cut.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(3))
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(responseBody.asText()).isEmpty();
    }

    private static final class StubInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private StubInputStream(byte[] bytes) {
            delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {}
    }

    private static final class RecordingOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

        @Override
        public void write(int byteToWrite) {
            delegate.write(byteToWrite);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {}

        private String asText() {
            return delegate.toString(StandardCharsets.UTF_8);
        }
    }
}
