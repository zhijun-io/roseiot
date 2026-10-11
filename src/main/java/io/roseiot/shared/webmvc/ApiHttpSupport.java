package io.roseiot.shared.webmvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes JSON at the HTTP API boundary. */
@Component
public class ApiHttpSupport {

    private final JsonMapper jsonMapper;

    public ApiHttpSupport(JsonMapper jsonMapper) {
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
        ProblemDetail problem = problemDetail(status, code, message);
        response.setStatus(status);
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), problemRepresentation(problem));
    }

    public ResponseEntity<ProblemDetail> errorResponse(int status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(problemDetail(status, code, message));
    }

    public ProblemDetail problemDetail(int status, String code, String detail) {
        HttpStatusCode statusCode = HttpStatusCode.valueOf(status);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusCode, detail);
        problem.setType(URI.create("urn:roseiot:problem:" + code));
        problem.setTitle(statusCode instanceof HttpStatus httpStatus ? httpStatus.getReasonPhrase() : "Request failed");
        problem.setProperty("code", code);
        String requestId = MDC.get(RequestCorrelationFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    private static Map<String, Object> problemRepresentation(ProblemDetail problem) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("type", problem.getType());
        representation.put("title", problem.getTitle());
        representation.put("status", problem.getStatus());
        representation.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            representation.put("instance", problem.getInstance());
        }
        if (problem.getProperties() != null) {
            representation.putAll(problem.getProperties());
        }
        return representation;
    }
}
