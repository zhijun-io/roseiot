package io.roseiot.platform.support;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Tests {

    @Test
    void generatesVersion7Uuid() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }
}
