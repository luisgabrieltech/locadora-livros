package br.com.locadora;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.locadora.config.SecurityConfiguration;
import br.com.locadora.config.TimeConfiguration;
import br.com.locadora.system.StatusController;
import br.com.locadora.system.CsrfController;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

// Servidor real para exercitar ERROR dispatch, sem depender do banco.
// ApplicationIT continua verificando a aplicação completa com PostgreSQL.
@SpringBootTest(
        classes = SecurityHttpTest.HttpApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.config.import=",
            "app.operator.username=http-test-operator",
            "app.operator.password=http-test-only-password",
            "app.time-zone=America/Sao_Paulo"
        })
class SecurityHttpTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void preservesForbiddenResponseWhenCsrfIsMissing() throws Exception {
        var credentials = "http-test-operator:http-test-only-password";
        var authorization = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(uri("/api/v1/status"))
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void internalErrorPermissionDoesNotExposeDirectRequests() throws Exception {
        for (var path : new String[] {"/api/v1/status", "/error"}) {
            var request = HttpRequest.newBuilder(uri(path)).GET().build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("anonymous request to %s", path)
                    .isEqualTo(401);
        }
    }

    @Test
    void csrfSessionAllowsValidTokenButDoesNotAuthenticateRequests() throws Exception {
        var sessionClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
                "http-test-operator:http-test-only-password".getBytes(StandardCharsets.UTF_8));
        var csrfResponse = sessionClient.send(HttpRequest.newBuilder(uri("/api/v1/csrf"))
                .header("Authorization", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(csrfResponse.statusCode()).isEqualTo(200);
        assertThat(csrfResponse.headers().allValues("Set-Cookie")).isNotEmpty();
        var csrf = JsonMapper.builder().build().readTree(csrfResponse.body());
        var post = HttpRequest.newBuilder(uri("/api/v1/status"))
                .header("Authorization", authorization)
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        // O token permite chegar ao MVC; status só tem GET, portanto retorna 405.
        assertThat(sessionClient.send(post, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
        // Reutilizar o token deve funcionar também após autenticações subsequentes.
        assertThat(sessionClient.send(post, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
        var authenticatedGet = HttpRequest.newBuilder(uri("/api/v1/status"))
                .header("Authorization", authorization).GET().build();
        assertThat(sessionClient.send(authenticatedGet, HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(200);
        assertThat(sessionClient.send(post, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
        assertThat(client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        var anonymous = HttpRequest.newBuilder(uri("/api/v1/status")).GET().build();
        assertThat(sessionClient.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
    })
    @Import({SecurityConfiguration.class, TimeConfiguration.class, StatusController.class, CsrfController.class})
    static class HttpApplication {}
}
