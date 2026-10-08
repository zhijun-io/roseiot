package io.roseiot.platform.auth;

import java.security.SecureRandom;
import java.util.Base64;

final class OpaqueTokens {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OpaqueTokens() {}

    static String generateUrlSafe256Bit() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
