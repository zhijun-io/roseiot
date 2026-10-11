package io.roseiot.shared.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Set;

/** Applies tenant predicates to tenant-owned tables; identity and control-plane tables stay global. */
public final class RoseiotTenantLineHandler implements TenantLineHandler {

    private static final Set<String> GLOBAL_TABLES = Set.of(
            "users",
            "resource",
            "user_credential",
            "system_setting",
            "tenant");

    @Override
    public Expression getTenantId() {
        if (TenantContext.current() != null) return new LongValue(TenantContext.current());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal) {
            if (principal.tenantId() == null) throw new AccessDeniedException("Tenant context required");
            return new LongValue(principal.tenantId());
        }
        throw new AccessDeniedException("Tenant context required");
    }

    @Override
    public boolean ignoreTable(String tableName) {
        String normalized = tableName
                .replace("\"", "")
                .replace("`", "")
                .toLowerCase(Locale.ROOT);
        int schemaSeparator = normalized.lastIndexOf('.');
        if (schemaSeparator >= 0) {
            normalized = normalized.substring(schemaSeparator + 1);
        }
        if (GLOBAL_TABLES.contains(normalized)) {
            return true;
        }

        return false;
    }
}
