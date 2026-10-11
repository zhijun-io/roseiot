package io.roseiot.iam.role;

import java.util.List;

public record RoleInfo(Long id, String name, List<String> actionCodes) {

    public RoleInfo {
        actionCodes = List.copyOf(actionCodes);
    }
}
