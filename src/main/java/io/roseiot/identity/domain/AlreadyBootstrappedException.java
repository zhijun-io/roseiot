package io.roseiot.identity.domain;

public class AlreadyBootstrappedException extends RuntimeException {

    public AlreadyBootstrappedException() {
        super("Platform is already bootstrapped");
    }
}
