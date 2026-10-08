package io.roseiot;

import io.roseiot.test.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorHealthIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static PostgreSQLContainer<?> postgres = PostgresTestSupport.newContainer();

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerDatasource(registry, postgres);
    }

    @LocalServerPort
    int port;

    @Test
    void livenessIsAnonymousAndDoesNotLeakSecrets() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/actuator/health/liveness");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).doesNotContain("jdbc:", "password", "SPRING_DATASOURCE");
    }

    @Test
    void readinessIsUpWhenDatabaseIsAvailable() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void nonHealthEndpointsAreNotPublic() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
