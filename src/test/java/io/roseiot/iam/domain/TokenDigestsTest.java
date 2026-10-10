package io.roseiot.iam.domain;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDigestsTest {

    @Test
    void shouldMatchPublishedSha256Vector() {
        assertThat(TokenDigests.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void shouldDigestEmptyTokenToItsKnownVector() {
        assertThat(TokenDigests.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void shouldBeDeterministicSoStoredHashCanMatchARecomputedLookup() {
        assertThat(TokenDigests.sha256Hex("refresh-token-value"))
                .isEqualTo(TokenDigests.sha256Hex("refresh-token-value"));
    }

    @Test
    void shouldProduceSixtyFourLowercaseHexChars() {
        assertThat(TokenDigests.sha256Hex("anything")).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldChangeDigestForSingleCharacterDifference() {
        assertThat(TokenDigests.sha256Hex("a")).isNotEqualTo(TokenDigests.sha256Hex("b"));
    }

    @Test
    void shouldEncodeUrlSafeTokenWithoutPadding() {
        String token = TokenDigests.generateUrlSafe256Bit();

        assertThat(token).hasSize(43).doesNotContain("=", "+", "/");
    }

    @Test
    void shouldGenerateTwoHundredFiftySixBitsOfEntropy() {
        String token = TokenDigests.generateUrlSafe256Bit();

        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void shouldNotRepeatTokensAcrossBatches() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            tokens.add(TokenDigests.generateUrlSafe256Bit());
        }

        assertThat(tokens).hasSize(1_000);
    }
}
