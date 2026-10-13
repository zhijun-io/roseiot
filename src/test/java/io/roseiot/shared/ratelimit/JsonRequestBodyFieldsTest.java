package io.roseiot.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonRequestBodyFieldsTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void shouldReadTextFieldForRateLimitKey() {
        byte[] body = body("""
                {"email":"ops@roseiot.io","password":"correct horse 42"}
                """);

        Optional<String> value = JsonRequestBodyFields.readTextField(body, "email", jsonMapper);

        assertThat(value).contains("ops@roseiot.io");
    }

    @Test
    void shouldReturnEmptyWhenFieldIsAbsent() {
        byte[] body = body("""
                {"password":"correct horse 42"}
                """);

        assertThat(JsonRequestBodyFields.readTextField(body, "email", jsonMapper))
                .isEmpty();
    }

    @Test
    void shouldTreatJsonNullAsAbsent() {
        byte[] body = body("""
                {"email":null}
                """);

        assertThat(JsonRequestBodyFields.readTextField(body, "email", jsonMapper))
                .isEmpty();
    }

    @Test
    void shouldTreatBlankValueAsAbsent() {
        byte[] body = body("""
                {"email":"   "}
                """);

        assertThat(JsonRequestBodyFields.readTextField(body, "email", jsonMapper))
                .isEmpty();
    }

    @Test
    void shouldPreserveValueBytesWithoutTrimming() {
        byte[] body = body("""
                {"email":" ops@roseiot.io "}
                """);

        assertThat(JsonRequestBodyFields.readTextField(body, "email", jsonMapper))
                .contains(" ops@roseiot.io ");
    }

    @Test
    void shouldNotReadAnObjectNodeAsText() {
        byte[] text = body("""
                {"email":"a@b.co"}
                """);
        byte[] object = body("""
                {"email":{"work":"a@b.co"}}
                """);

        assertThat(JsonRequestBodyFields.readTextField(text, "email", jsonMapper))
                .contains("a@b.co");
        assertThat(JsonRequestBodyFields.readTextField(object, "email", jsonMapper))
                .isEmpty();
    }

    @Test
    void shouldSwallowMalformedJsonBecauseRateLimitingMustNotBreakTheRequest() {
        assertThat(JsonRequestBodyFields.readTextField(body("{not json"), "email", jsonMapper))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForNullAndEmptyBody() {
        assertThat(JsonRequestBodyFields.readTextField(null, "email", jsonMapper))
                .isEmpty();
        assertThat(JsonRequestBodyFields.readTextField(new byte[0], "email", jsonMapper))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenRootIsNotAnObject() {
        assertThat(JsonRequestBodyFields.readTextField(body("""
                [1,2,3]
                """), "email", jsonMapper))
                .isEmpty();
    }
}
