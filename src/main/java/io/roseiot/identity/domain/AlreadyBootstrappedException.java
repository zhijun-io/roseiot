package io.roseiot.identity.domain;

public class AlreadyBootstrappedException extends RuntimeException {

    public AlreadyBootstrappedException() {
        super("System is already bootstrapped");
    }
}
