package io.roseiot.shared.webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CachedBodyHttpServletRequestTest {

    @Test
    void shouldServeCachedBodyFromInputStream() throws IOException {
        byte[] body = "{\"email\":\"ops@roseiot.io\"}".getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest cut = new CachedBodyHttpServletRequest(new MockHttpServletRequest(), body);

        try (var in = cut.getInputStream()) {
            assertThat(in.isFinished()).isFalse();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("{\"email\":\"ops@roseiot.io\"}");
            assertThat(in.isFinished()).isTrue();
            assertThat(in.isReady()).isTrue();
        }
    }

    @Test
    void shouldTreatNullBodyAsEmptyStream() throws IOException {
        CachedBodyHttpServletRequest cut = new CachedBodyHttpServletRequest(new MockHttpServletRequest(), null);

        try (var in = cut.getInputStream()) {
            assertThat(in.isFinished()).isTrue();
            assertThat(in.readAllBytes()).isEmpty();
        }
    }

    @Test
    void shouldAllowRepeatedReadsOfSameBody() throws IOException {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest cut = new CachedBodyHttpServletRequest(new MockHttpServletRequest(), body);

        assertThat(cut.getInputStream().readAllBytes()).isEqualTo(body);
        assertThat(cut.getInputStream().readAllBytes()).isEqualTo(body);
    }
}
