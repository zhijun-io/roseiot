package io.roseiot;

import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoseiotApplicationBootstrapFailureTest {

    @Test
    void shouldPrintBootstrapValidationDetailOnCliFailure() {
        BusinessException failure = new BusinessException(ApiErrorCode.BOOTSTRAP_VALIDATION, "Invalid email");

        assertThat(RoseiotApplication.bootstrapFailureMessage(failure)).isEqualTo("Invalid email");
    }

    @Test
    void shouldPrintAlreadyBootstrappedDetailOnCliFailure() {
        BusinessException failure =
                new BusinessException(ApiErrorCode.ALREADY_BOOTSTRAPPED, "System is already bootstrapped");

        assertThat(RoseiotApplication.bootstrapFailureMessage(failure)).isEqualTo("System is already bootstrapped");
    }
}
