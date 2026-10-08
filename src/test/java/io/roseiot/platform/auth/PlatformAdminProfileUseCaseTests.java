package io.roseiot.platform.auth;

import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminProfileUseCaseTests {

    @Mock
    PlatformAdminMapper platformAdminMapper;

    @InjectMocks
    PlatformAdminProfileUseCase profileUseCase;

    @Test
    void getProfile() {
        UUID id = UUID.randomUUID();
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(id);
        admin.setEmail("admin@example.com");
        when(platformAdminMapper.selectById(id)).thenReturn(admin);

        var profile = profileUseCase.getProfile(id);

        assertThat(profile.id()).isEqualTo(id);
        assertThat(profile.email()).isEqualTo("admin@example.com");
    }

    @Test
    void getProfileRejectsMissingAdmin() {
        UUID id = UUID.randomUUID();
        when(platformAdminMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> profileUseCase.getProfile(id)).isInstanceOf(InvalidCredentialsException.class);
    }
}
