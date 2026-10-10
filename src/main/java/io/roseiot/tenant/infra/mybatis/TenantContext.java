package io.roseiot.tenant.infra.mybatis;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Explicit scope for verified maintenance, platform and token-based operations.
 */
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID current() {
        return CURRENT.get();
    }

    public static <T> T call(UUID tenantId, Supplier<T> operation) {
        UUID previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(tenantId));
        try {
            return operation.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
