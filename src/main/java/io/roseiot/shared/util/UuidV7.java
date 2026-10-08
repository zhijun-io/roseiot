package io.roseiot.platform.support;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7 (time-ordered, opaque). Used for stage 1+ external identifiers.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        long timestampMs = System.currentTimeMillis();
        long rand = RANDOM.nextLong();

        long msb = (timestampMs & 0xFFFFFFFFFFFFL) << 16;
        msb |= (rand >>> 48) & 0x0FFFL;
        msb = (msb & 0xFFFFFFFFFFFF0FFFL) | 0x7000L;

        long lsb = (rand & 0x0000FFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
