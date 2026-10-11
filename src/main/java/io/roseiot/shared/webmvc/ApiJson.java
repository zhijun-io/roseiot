package io.roseiot.shared.webmvc;

import io.roseiot.shared.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reads and writes JSON at the HTTP API boundary. */
@Component
public class ApiJson {

    private final JsonMapper jsonMapper;

    public ApiJson(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public <T> T readBody(HttpServletRequest request, Class<T> bodyType)
            throws IOException {
        return jsonMapper.readValue(request.getInputStream(), bodyType);
    }

    public void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }

    public void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        writeJson(response, status, new ApiErrorResponse(code, message));
    }

    public ResponseEntity<ApiErrorResponse> errorResponse(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
