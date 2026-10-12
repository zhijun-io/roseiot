package io.roseiot.shared.ratelimit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

final class JsonRequestBodyFields {

    private JsonRequestBodyFields() {}

    static Optional<String> readTextField(byte[] body, String fieldName, JsonMapper jsonMapper) {
        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        try {
            JsonNode root = jsonMapper.readTree(body);
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            JsonNode node = root.get(fieldName);
            if (node == null || node.isNull()) {
                return Optional.empty();
            }
            String value = node.asString(null);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (JacksonException _) {
            // This is only a best-effort rate-limit key lookup; request parsing reports malformed JSON downstream.
            return Optional.empty();
        }
    }
}
