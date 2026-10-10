package io.roseiot.iam.application;

import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
    private UserMapper userMapper;
    private UserService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        cut = new UserService(userMapper);
    }

    @Test
    void shouldReturnProfileForExistingUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        UserService.UserProfile profile = cut.getProfile(USER_ID);

        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.email()).isEqualTo("ops@roseiot.io");
    }

    @Test
    void shouldHideAccountExistenceFromProfileLookup() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.getProfile(USER_ID)).isInstanceOf(BadCredentialsException.class);
    }
}
