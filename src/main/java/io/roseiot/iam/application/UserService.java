package io.roseiot.iam.application;

import io.roseiot.iam.infra.security.AuthenticationExceptionMapping;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserProfile getProfile(UUID userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw AuthenticationExceptionMapping.invalidCredentials();
        return new UserProfile(user.getId(), user.getEmail());
    }

    public record UserProfile(UUID id, String email) {}
}
