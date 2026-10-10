package io.roseiot.iam.application;

import io.roseiot.iam.infra.security.AuthenticationExceptionMapping;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        return profile(user);
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, String name) {
        User user = userMapper.selectById(userId);
        if (user == null) throw AuthenticationExceptionMapping.invalidCredentials();
        user.setName(name.trim());
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);
        return profile(user);
    }

    private static UserProfile profile(User user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getName());
    }

    public record UserProfile(UUID id, String email, String name) {}
}
