package io.roseiot.shared.webmvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RequestBodyLimitFilterTest {

    @Test
    void rejectsOversizedChunkedBodyOnUnmatchedRoute() throws Exception {
        HttpServletRequest request =
                request("POST", "/api/users", "0123456789abcdefg".getBytes(StandardCharsets.UTF_8));
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStream(responseBody));
        FilterChain chain = mock(FilterChain.class);

        filter(16).doFilterInternal(request, response, chain);

        verify(response).setStatus(413);
        verify(chain, never()).doFilter(any(), any());
        assertThat(responseBody.toString(StandardCharsets.UTF_8)).contains("request_too_large");
    }

    @Test
    void passesBoundedBodyToUnmatchedRoute() throws Exception {
        byte[] body = "{\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8);
        HttpServletRequest request = request("POST", "/api/users", body);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter(32).doFilterInternal(request, response, chain);

        org.mockito.ArgumentCaptor<HttpServletRequest> forwarded =
                org.mockito.ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(forwarded.capture(), any(HttpServletResponse.class));
        assertThat(forwarded.getValue().getInputStream().readAllBytes()).isEqualTo(body);
    }

    private static RequestBodyLimitFilter filter(int maxBodyBytes) {
        return new RequestBodyLimitFilter(
                new RequestBodyProperties(maxBodyBytes, maxBodyBytes),
                new ApiHttpSupport(JsonMapper.builder().build()));
    }

    private static HttpServletRequest request(String method, String path, byte[] body) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getServletPath()).thenReturn(path);
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getInputStream()).thenReturn(inputStream(body));
        return request;
    }

    private static ServletInputStream inputStream(byte[] body) {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return input.read();
            }

            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {}
        };
    }

    private static ServletOutputStream outputStream(ByteArrayOutputStream output) {
        return new ServletOutputStream() {
            @Override
            public void write(int value) {
                output.write(value);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {}
        };
    }
}
