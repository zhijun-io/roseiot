package io.roseiot.iam.user;

import java.util.List;

public record UserAuthentication(User user, String password, List<String> permissions) {}
