package io.roseiot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 不可达数据源场景：不加载 {@link TestcontainersConfig}，不启嵌入式 Tomcat。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unreachable",
            "spring.datasource.username=unreachable",
            "spring.datasource.password=unreachable",
                "spring.datasource.hikari.initialization-fail-timeout=-1",
                // 不可达主机仍走 Hikari 取连接；默认 30s 超时会拖慢本用例（AC-3 语义不变）。
                "spring.datasource.hikari.connection-timeout=500",
                "spring.datasource.hikari.validation-timeout=500",
                "management.endpoint.health.timeout-per-health-indicator=1s"
        })
@AutoConfigureMockMvc
class ActuatorHealthWhenDatabaseUnreachableTests{

    @Autowired
    MockMvcTester mvc;

    @Test
    void readinessIsDownButLivenessStillUp() {
        mvc.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .assertThat()
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);

        mvc.get().uri("/actuator/health/liveness").exchange().assertThat().hasStatusOk();
    }
}
