package io.roseiot.platform.bootstrap;

public class AlreadyBootstrappedException extends RuntimeException {

    public AlreadyBootstrappedException() {
        super("Platform is already bootstrapped");
    }
}
