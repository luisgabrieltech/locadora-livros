package br.com.locadora.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=",
    "app.operator.username=client-test-operator",
    "app.operator.password=client-test-only-password"
})
class ClientesIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.7-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private HttpClient client;
    private String token;
    private String headerName;

    @BeforeEach
    void preparar() throws Exception {
        // Banco exclusivo e descartável criado pelo container desta classe.
        jdbc.update("DELETE FROM locadora.clientes");
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var csrf = enviar("GET", "/api/v1/csrf", null, true, false);
        assertThat(csrf.statusCode()).isEqualTo(200);
        assertThat(csrf.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(csrf.headers().allValues("Set-Cookie")).isNotEmpty();
        var body = json.readTree(csrf.body());
        token = body.get("token").asText();
        headerName = body.get("headerName").asText();
    }

    @Test
    void criaConsultaAtualizaERemoveEmail() throws Exception {
        String cpf = ClienteTestData.cpf(1);
        var criado = enviar("POST", "/api/v1/clientes", dados(ClienteTestData.formatar(cpf)), true, true);
        assertThat(criado.statusCode()).isEqualTo(201);
        var body = json.readTree(criado.body());
        String path = "/api/v1/clientes/" + body.get("id").asText();
        assertThat(criado.headers().firstValue("Location")).contains(path);
        assertThat(body.get("cpf").asText()).isEqualTo(cpf);
        assertThat(body.get("nome").asText()).isEqualTo("Cliente Teste");
        assertThat(body.get("telefone").asText()).isEqualTo("11999990000");
        assertThat(enviar("GET", path, null, true, false).statusCode()).isEqualTo(200);

        var atualizado = enviar("PUT", path,
                "{\"nome\":\"Novo Nome\",\"telefone\":\"11999990000\",\"cpf\":\"" + cpf + "\"}", true, true);
        assertThat(atualizado.statusCode()).isEqualTo(200);
        var updated = json.readTree(atualizado.body());
        assertThat(updated.get("id").asText()).isEqualTo(body.get("id").asText());
        assertThat(updated.get("email").isNull()).isTrue();
        assertThat(json.readTree(enviar("GET", path, null, true, false).body()).get("nome").asText())
                .isEqualTo("Novo Nome");
    }

    @Test
    void rejeitaDuplicidadeFormatadaEAlteracaoParaCpfDeOutroCliente() throws Exception {
        String cpf = ClienteTestData.cpf(1);
        assertThat(enviar("POST", "/api/v1/clientes", dados(cpf), true, true).statusCode()).isEqualTo(201);
        var duplicado = enviar("POST", "/api/v1/clientes", dados(ClienteTestData.formatar(cpf)), true, true);
        problema(duplicado, 409);
        assertThat(duplicado.body()).doesNotContain(cpf, "uk_clientes", "INSERT", "stackTrace");

        var segundo = enviar("POST", "/api/v1/clientes", dados(ClienteTestData.cpf(2)), true, true);
        String id = json.readTree(segundo.body()).get("id").asText();
        problema(enviar("PUT", "/api/v1/clientes/" + id, dados(cpf), true, true), 409);
        assertThat(jdbc.queryForObject("SELECT cpf FROM locadora.clientes WHERE id = ?", String.class,
                UUID.fromString(id))).isEqualTo(ClienteTestData.cpf(2));
    }

    @Test
    void rejeitaDadosInvalidosSemPersistirOuExporValores() throws Exception {
        for (String payload : new String[] {"{}", "{", dados("11111111111"),
                dados(ClienteTestData.cpf(1)).replace("(11) 99999-0000", "telefone-invalido"),
                dados(ClienteTestData.cpf(1)).replace("cliente@example.test", "email-invalido")}) {
            var response = enviar("POST", "/api/v1/clientes", payload, true, true);
            problema(response, 400);
            assertThat(response.body()).doesNotContain("11111111111", "telefone-invalido", "email-invalido");
        }
        var response = enviar("POST", "/api/v1/clientes", "{}", true, true);
        assertThat(json.readTree(response.body()).get("errors").has("cpf")).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.clientes", Integer.class)).isZero();
    }

    @Test
    void trataIdsAusentesEInvalidos() throws Exception {
        String ausente = "/api/v1/clientes/" + UUID.randomUUID();
        problema(enviar("GET", ausente, null, true, false), 404);
        problema(enviar("PUT", ausente, dados(ClienteTestData.cpf(1)), true, true), 404);
        problema(enviar("GET", "/api/v1/clientes/id-invalido", null, true, false), 400);
    }

    @Test
    void paginaSemExporCpfEValidaParametros() throws Exception {
        var vazia = json.readTree(enviar("GET", "/api/v1/clientes", null, true, false).body());
        assertThat(vazia.get("items").size()).isZero();
        for (int seed = 1; seed <= 3; seed++) {
            assertThat(enviar("POST", "/api/v1/clientes", dados(ClienteTestData.cpf(seed)), true, true)
                    .statusCode()).isEqualTo(201);
        }
        var pagina = json.readTree(enviar("GET", "/api/v1/clientes?page=0&size=2", null, true, false).body());
        assertThat(pagina.get("items").size()).isEqualTo(2);
        assertThat(pagina.get("totalElements").asInt()).isEqualTo(3);
        assertThat(pagina.get("totalPages").asInt()).isEqualTo(2);
        for (JsonNode item : pagina.get("items")) {
            assertThat(item.has("cpf")).isFalse();
        }
        var repetida = json.readTree(enviar("GET", "/api/v1/clientes?page=0&size=2", null, true, false).body());
        assertThat(repetida.get("items")).isEqualTo(pagina.get("items"));
        var fora = json.readTree(enviar("GET", "/api/v1/clientes?page=99", null, true, false).body());
        assertThat(fora.get("items").size()).isZero();
        for (String parametros : new String[] {"page=-1", "size=0", "size=101", "page=abc"}) {
            problema(enviar("GET", "/api/v1/clientes?" + parametros, null, true, false), 400);
        }
    }

    @Test
    void exigeAutenticacaoCookieEToken() throws Exception {
        assertThat(enviar("GET", "/api/v1/clientes", null, false, false).statusCode()).isEqualTo(401);
        assertThat(enviar("GET", "/api/v1/csrf", null, false, false).statusCode()).isEqualTo(401);
        assertThat(enviar("POST", "/api/v1/clientes", dados(ClienteTestData.cpf(1)), true, false).statusCode())
                .isEqualTo(403);
        String original = token;
        token = "token-invalido";
        assertThat(enviar("POST", "/api/v1/clientes", dados(ClienteTestData.cpf(1)), true, true).statusCode())
                .isEqualTo(403);
        token = original;
        var wrongCredentials = requisicao("POST", "/api/v1/clientes", dados(ClienteTestData.cpf(1)), false, true)
                .header("Authorization", basic("client-test-operator:senha-incorreta")).build();
        assertThat(client.send(wrongCredentials, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        // O cookie CSRF sozinho não autentica o operador.
        assertThat(enviar("GET", "/api/v1/clientes", null, false, false).statusCode()).isEqualTo(401);
        var semCookie = HttpClient.newHttpClient().send(
                requisicao("POST", "/api/v1/clientes", dados(ClienteTestData.cpf(1)), true, true).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(semCookie.statusCode()).isEqualTo(403);
    }

    @Test
    void concorrenciaMantemUmUnicoCliente() throws Exception {
        String payload = dados(ClienteTestData.cpf(50));
        var primeira = client.sendAsync(requisicao("POST", "/api/v1/clientes", payload, true, true).build(),
                HttpResponse.BodyHandlers.ofString());
        var segunda = client.sendAsync(requisicao("POST", "/api/v1/clientes", payload, true, true).build(),
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(primeira, segunda).join();
        assertThat(new int[] {primeira.join().statusCode(), segunda.join().statusCode()})
                .containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.clientes", Integer.class)).isEqualTo(1);
    }

    @Test
    void bancoProtegeUnicidadeMesmoSemPreConsulta() throws Exception {
        String cpf = ClienteTestData.cpf(1);
        assertThat(enviar("POST", "/api/v1/clientes", dados(cpf), true, true).statusCode()).isEqualTo(201);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO locadora.clientes (id, nome, telefone, cpf) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), "Fixture", "11999990000", cpf))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void problema(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
    }

    private String dados(String cpf) {
        return "{\"nome\":\"  Cliente Teste  \",\"telefone\":\"(11) 99999-0000\",\"cpf\":\"" + cpf
                + "\",\"email\":\"cliente@example.test\"}";
    }

    private HttpResponse<String> enviar(String method, String path, String body, boolean auth, boolean csrf)
            throws Exception {
        return client.send(requisicao(method, path, body, auth, csrf).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder requisicao(String method, String path, String body, boolean auth, boolean csrf) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (auth) {
            request.header("Authorization", basic("client-test-operator:client-test-only-password"));
        }
        if (csrf) {
            request.header(headerName, token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
    }

    private String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
