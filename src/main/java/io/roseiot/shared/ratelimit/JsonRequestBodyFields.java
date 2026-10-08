package io.roseiot.shared.ratelimit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

public final class JsonRequestBodyFields {

    private JsonRequestBodyFields() {}

    public static Optional<String> readTextField(byte[] body, String fieldName, JsonMapper jsonMapper) {
        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        try {
            JsonNode root = jsonMapper.readTree(body);
            JsonNode node = root.get(fieldName);
            if (node == null || node.isNull()) {
                return Optional.empty();
            }
            String value = node.asString(null);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
