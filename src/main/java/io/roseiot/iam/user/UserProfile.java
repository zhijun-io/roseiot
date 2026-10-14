package io.roseiot.iam.user;

public record UserProfile(Long id, String email, String name) {

    static UserProfile from(UserEntity user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getName());
    }
}
