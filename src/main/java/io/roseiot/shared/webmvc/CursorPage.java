package io.roseiot.shared.webmvc;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.ToLongFunction;

public record CursorPage<T>(List<T> items, String nextCursor) {

    public CursorPage {
        items = List.copyOf(items);
    }

    public static long afterId(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII));
            if (id <= 0) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Invalid cursor");
        }
    }

    public static int checkedLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "limit must be between 1 and 100");
        }
        return limit;
    }

    public static <T> CursorPage<T> fromRows(List<T> rows, int limit, ToLongFunction<T> id) {
        boolean hasMore = rows.size() > limit;
        List<T> items = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                        Long.toString(id.applyAsLong(items.getLast())).getBytes(StandardCharsets.US_ASCII))
                : null;
        return new CursorPage<>(items, nextCursor);
    }
}
