package io.roseiot.tenant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TenantService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantMapper tenantMapper;
    public TenantService(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    public void requireCustomerTenant(Long tenantId) {
        if (Tenant.DEFAULT_TENANT_ID.equals(tenantId)) {
            throw new AccessDeniedException("Reserved tenant");
        }
    }

    public void requireActive(Long tenantId) {
        ensureActive(tenantMapper.selectById(tenantId));
    }

    public void requireActiveForUpdate(Long tenantId) {
        ensureActive(tenantMapper.selectByIdForUpdate(tenantId));
    }

    @PreAuthorize("hasAuthority('tenant.read')")
    public org.springframework.data.domain.Page<Tenant> list(Pageable pageable) {
        Pageable checkedPageable = checkedPageable(pageable);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TenantEntity> result = tenantMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        checkedPageable.getPageNumber() + 1L, checkedPageable.getPageSize()),
                Wrappers.lambdaQuery(TenantEntity.class).orderByAsc(TenantEntity::getId));
        List<Tenant> items = result.getRecords().stream().map(Tenant::from).toList();
        return new PageImpl<>(items, checkedPageable, result.getTotal());
    }

    private static Pageable checkedPageable(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100
                || !Sort.by(Sort.Order.asc("id")).equals(pageable.getSort())) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Use a page size from 1 to 100 and sort by id ascending");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
    }

    @PreAuthorize("hasAuthority('tenant.read')")
    public Tenant get(Long tenantId) {
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) throw new BusinessException(ApiErrorCode.TENANT_NOT_FOUND);
        return Tenant.from(tenant);
    }

    public boolean isActive(Long tenantId) {
        return tenantId != null && isActive(tenantMapper.selectById(tenantId));
    }

    @Transactional
    @PreAuthorize("hasAuthority('tenant.create')")
    public Tenant create(String name) {
        TenantEntity tenant = new TenantEntity();
        tenant.setCode(generateCode());
        tenant.setName(name.trim());
        tenant.setStatus(TenantStatus.ACTIVE);

        tenantMapper.insert(tenant);
        return Tenant.from(tenant);
    }

    @Transactional
    @PreAuthorize("(#p2 == null or (#p2 ? hasAuthority('tenant.disable') : hasAuthority('tenant.enable')))"
            + " and (#p1 == null or #p1.isBlank() or hasAuthority('tenant.create'))")
    public Tenant update(Long tenantId, String name, Boolean disabled) {
        TenantEntity tenant = tenantMapper.selectByIdForUpdate(tenantId);
        if (tenant == null) throw new BusinessException(ApiErrorCode.TENANT_NOT_FOUND);
        requireCustomerTenant(tenantId);
        TenantStatus status = tenant.getStatus();
        if (disabled != null) {
            status = disabled ? TenantStatus.DISABLED : TenantStatus.ACTIVE;
        }
        if (name != null && !name.isBlank()) {
            tenant.setName(name.trim());
        }
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
        return Tenant.from(tenant);
    }

    private static void ensureActive(TenantEntity tenant) {
        if (!isActive(tenant)) {
            throw new BusinessException(ApiErrorCode.TENANT_NOT_FOUND);
        }
    }

    private static boolean isActive(TenantEntity tenant) {
        return tenant != null && tenant.getStatus() == TenantStatus.ACTIVE;
    }

    private static String generateCode() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
