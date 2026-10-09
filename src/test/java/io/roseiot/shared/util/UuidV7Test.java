package io.roseiot.shared.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void shouldSetVersionSevenNibble() {
        UUID cut = UuidV7.generate();

        assertThat((cut.getMostSignificantBits() >>> 12) & 0xF).isEqualTo(0x7);
    }

    @Test
    void shouldSetRfc9562VariantBits() {
        UUID cut = UuidV7.generate();

        assertThat(cut.getLeastSignificantBits() >>> 62).isEqualTo(0b10);
    }

    @Test
    void shouldEmbedCurrentTimestampInLeadingFortyEightBits() {
        long before = System.currentTimeMillis();
        UUID cut = UuidV7.generate();
        long after = System.currentTimeMillis();

        assertThat(cut.getMostSignificantBits() >>> 16).isBetween(before, after);
    }

    @Test
    void shouldNotGoBackwardsInTimeAcrossConsecutiveIds() {
        UUID first = UuidV7.generate();
        UUID second = UuidV7.generate();

        assertThat(second.getMostSignificantBits() >>> 16)
                .isGreaterThanOrEqualTo(first.getMostSignificantBits() >>> 16);
    }

    @Test
    void shouldKeepRandomBitsOpaqueRatherThanZeroFilled() {
        Set<Long> randomParts = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            UUID generated = UuidV7.generate();
            long high = (generated.getMostSignificantBits() & 0xFFFL) << 48;
            long low = generated.getLeastSignificantBits() & 0x0000FFFFFFFFFFFFL;
            randomParts.add(high | low);
        }

        assertThat(randomParts).hasSize(100);
    }

    @Test
    void shouldGenerateDistinctIds() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidV7.generate());
        }

        assertThat(ids).hasSize(10_000);
    }

}
