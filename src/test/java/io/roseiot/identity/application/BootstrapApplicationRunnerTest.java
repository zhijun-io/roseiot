package io.roseiot.identity.application;

import io.roseiot.identity.domain.BootstrapValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BootstrapApplicationRunnerTest {

    private Environment environment;
    private BootstrapUserService bootstrapUserService;
    private BootstrapApplicationRunner cut;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        bootstrapUserService = mock(BootstrapUserService.class);
        cut = new BootstrapApplicationRunner(environment, bootstrapUserService);
    }

    @Test
    void shouldBootstrapUserWhenEmailAndPasswordAreConfigured() {
        when(environment.getProperty("BOOTSTRAP_USER_EMAIL")).thenReturn("ops@roseiot.io");
        when(environment.getProperty("BOOTSTRAP_USER_PASSWORD")).thenReturn("correct horse 42");

        cut.run(mock(ApplicationArguments.class));

        verify(bootstrapUserService).bootstrap("ops@roseiot.io", "correct horse 42");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBootstrapWhenEmailIsMissingOrBlank(String email) {
        when(environment.getProperty("BOOTSTRAP_USER_EMAIL")).thenReturn(email);
        when(environment.getProperty("BOOTSTRAP_USER_PASSWORD")).thenReturn("correct horse 42");

        assertThatThrownBy(() -> cut.run(mock(ApplicationArguments.class)))
                .isInstanceOf(BootstrapValidationException.class)
                .hasMessageContaining("BOOTSTRAP_USER_EMAIL");
        verifyNoInteractions(bootstrapUserService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBootstrapWhenPasswordIsMissingOrBlank(String password) {
        when(environment.getProperty("BOOTSTRAP_USER_EMAIL")).thenReturn("ops@roseiot.io");
        when(environment.getProperty("BOOTSTRAP_USER_PASSWORD")).thenReturn(password);

        assertThatThrownBy(() -> cut.run(mock(ApplicationArguments.class)))
                .isInstanceOf(BootstrapValidationException.class)
                .hasMessageContaining("BOOTSTRAP_USER_PASSWORD");
        verify(bootstrapUserService, never()).bootstrap(anyString(), anyString());
    }
}
