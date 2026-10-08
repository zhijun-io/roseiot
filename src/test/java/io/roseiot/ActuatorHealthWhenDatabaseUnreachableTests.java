package io.roseiot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

/** 不可达数据源场景：不加载 {@link TestcontainersConfig}，仅用 {@link RestTestClient}。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unreachable",
            "spring.datasource.username=unreachable",
            "spring.datasource.password=unreachable",
            "spring.datasource.hikari.initialization-fail-timeout=-1"
        })
@AutoConfigureRestTestClient
class ActuatorHealthWhenDatabaseUnreachableTests {

    @Autowired
    RestTestClient restTestClient;

    @Test
    void readinessIsDownButLivenessStillUp() {
        restTestClient
                .get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        restTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    }
}
