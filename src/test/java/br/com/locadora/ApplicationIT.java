package br.com.locadora;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.config.import=",
            "app.operator.username=integration-operator",
            "app.operator.password=integration-only-password",
            "app.time-zone=America/Sao_Paulo"
        })
class ApplicationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.7-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Clock clock;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void startsWithPostgresAndAppliesMigration() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'locadora'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(clock.getZone().getId()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        var response = getStatus(null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).isPresent();
    }

    @Test
    void rejectsIncorrectPassword() throws Exception {
        assertThat(getStatus("integration-operator:wrong-password").statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsAnonymousDirectAccessToErrorEndpoint() throws Exception {
        var request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/error"))
                .GET()
                .build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    @Test
    void returnsStatusForAuthenticatedOperatorWithoutCreatingSession() throws Exception {
        var response = getStatus("integration-operator:integration-only-password");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"application\":\"locadora-livros\"", "\"status\":\"UP\"",
                        "\"timeZone\":\"America/Sao_Paulo\"");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void keepsCsrfProtectionForUnsafeRequests() throws Exception {
        var request = HttpRequest.newBuilder(statusUri())
                .header("Authorization", basic("integration-operator:integration-only-password"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> getStatus(String credentials) throws Exception {
        var request = HttpRequest.newBuilder(statusUri()).GET();
        if (credentials != null) {
            request.header("Authorization", basic(credentials));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI statusUri() {
        return URI.create("http://127.0.0.1:" + port + "/api/v1/status");
    }

    private String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
