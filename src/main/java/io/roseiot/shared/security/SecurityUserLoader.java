package io.roseiot.shared.security;

/** Loads the current security principal for a token subject. */
public interface SecurityUserLoader {

    SecurityUser loadByUserId(Long userId);
}
