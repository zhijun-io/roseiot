package io.roseiot;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorHealthMockMvcTests extends AbstractIt {

    @Test
    void livenessIsAnonymousAndDoesNotLeakSecrets() {
        mvc.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .doesNotContain("jdbc:", "password", "SPRING_DATASOURCE");
    }

    @Test
    void readinessIsUpWhenDatabaseIsAvailable() {
        mvc.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .contains("\"status\":\"UP\"");
    }

    @Test
    void nonHealthEndpointsAreNotPublic() {
        var exchange = mvc.get().uri("/").exchange();
        assertThat(exchange.getResponse().getStatus())
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }
}
