package io.roseiot.iam.user;

import java.util.List;

record UserAuthentication(User user, String password, List<String> permissions) {

    public UserAuthentication {
        permissions = List.copyOf(permissions);
    }

    @Override
    public String toString() {
        return "UserAuthentication[redacted]";
    }
}
