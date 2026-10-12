package io.roseiot.shared.security;

/** Loads the current principal for the stable user ID in a JWT subject. */
public interface SecurityUserLoader {

    SecurityUser loadByUserId(Long userId);
}
