package io.roseiot.tenant.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;

public interface TenantInvitationMapper extends BaseMapper<TenantInvitation> {
    // The opaque token locates a specific notifier before a tenant principal exists.
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT i.* FROM tenant_invitation i
        JOIN tenant t ON t.id = i.tenant_id AND t.disabled_at IS NULL
        JOIN role r ON r.id = i.role_id AND r.tenant_id = i.tenant_id
        WHERE i.token_hash = #{tokenHash}
        """)
    TenantInvitation selectByToken(String tokenHash);
}
