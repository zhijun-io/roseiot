package io.roseiot.identity.infra.security.session;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

/** JSON bodies and error responses shared by session endpoint filters. */
final class SessionFilterSupport {

    private SessionFilterSupport() {}

    record LoginBody(String email, String password) {}

    record RefreshBody(String refreshToken) {}

    record TokenBody(String accessToken, String refreshToken) {}

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static <T> void readAndValidate(
            HttpServletRequest request,
            HttpServletResponse response,
            JsonMapper jsonMapper,
            ApiErrorBodyWriter apiErrorBodyWriter,
            Class<T> type,
            Predicate<T> validator,
            BodyHandler<T> handler)
            throws IOException {
        T body;
        try {
            body = jsonMapper.readValue(request.getInputStream(), type);
        } catch (IOException | RuntimeException exception) {
            if (isJsonParseFailure(exception)) {
                apiErrorBodyWriter.write(
                        response, HttpServletResponse.SC_BAD_REQUEST, "bad_request", "Malformed request body");
                return;
            }
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
        if (!validator.test(body)) {
            apiErrorBodyWriter.write(
                    response, HttpServletResponse.SC_BAD_REQUEST, "validation_error", "Validation failed");
            return;
        }
        handler.handle(body);
    }

    static void writeTokens(JsonMapper jsonMapper, HttpServletResponse response, String accessToken, String refreshToken)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), new TokenBody(accessToken, refreshToken));
    }

    private static boolean isJsonParseFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof JacksonException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    interface BodyHandler<T> {
        void handle(T body) throws IOException;
    }
}
