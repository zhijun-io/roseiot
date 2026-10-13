package io.roseiot.shared.mybatis;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Explicit scope for verified maintenance, platform and token-based operations.
 */
public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Long current() {
        return CURRENT.get();
    }

    public static <T> T call(Long tenantId, Supplier<T> operation) {
        Long previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(tenantId));
        try {
            return operation.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
