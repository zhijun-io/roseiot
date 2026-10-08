package io.roseiot.platform.auth.recovery;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryRecoveryNotificationAdapter implements RecoveryNotificationPort {

    private final CopyOnWriteArrayList<RecoveryNotification> notifications = new CopyOnWriteArrayList<>();

    @Override
    public void sendRecoveryEmail(String email, String plainToken) {
        notifications.add(new RecoveryNotification(email, plainToken));
    }

    public Optional<RecoveryNotification> lastForEmail(String email) {
        return notifications.stream()
                .filter(entry -> entry.email().equals(email))
                .reduce((first, second) -> second);
    }

    public void clear() {
        notifications.clear();
    }

    public record RecoveryNotification(String email, String token) {}
}
