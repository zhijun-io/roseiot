package io.roseiot.identity.infra.recovery;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryRecoveryNotifier implements RecoveryNotifier {

    private final CopyOnWriteArrayList<CapturedRecoveryEmail> captured = new CopyOnWriteArrayList<>();

    @Override
    public void sendRecoveryEmail(String email, String plainToken) {
        captured.add(new CapturedRecoveryEmail(email, plainToken));
    }

    public Optional<CapturedRecoveryEmail> lastForEmail(String email) {
        return captured.stream()
                .filter(entry -> entry.email().equals(email))
                .reduce((first, second) -> second);
    }

    public void clear() {
        captured.clear();
    }

    public record CapturedRecoveryEmail(String email, String token) {}
}
