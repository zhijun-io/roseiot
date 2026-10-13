package io.roseiot.iam.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.roseiot.iam.resource.ResourceService;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.tenant.TenantService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleServiceTest {

    private RoleMapper roleMapper;
    private RoleService cut;

    @BeforeEach
    void setUp() {
        roleMapper = mock(RoleMapper.class);
        cut = new RoleService(
                roleMapper, mock(RoleResourceMapper.class), mock(ResourceService.class), mock(TenantService.class));
    }

    @Test
    void rejectsInvalidRoleNameBeforeCreating() {
        assertThatThrownBy(() -> cut.create(42L, " ", Set.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(ApiErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(roleMapper);
    }

    @Test
    void rejectsInvalidRoleNameBeforeUpdating() {
        assertThatThrownBy(() -> cut.update(42L, 7L, "x".repeat(256), Set.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(ApiErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(roleMapper);
    }
}
