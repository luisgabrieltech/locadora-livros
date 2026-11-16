package br.com.locadora.catalogo;

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
    "app.operator.username=catalog-test-operator",
    "app.operator.password=catalog-test-only-password"
})
class CatalogoIT {
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
        // Limpeza apenas do banco descartável deste container, respeitando a FK.
        jdbc.update("DELETE FROM locadora.exemplares");
        jdbc.update("DELETE FROM locadora.livros");
        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var csrf = enviar("GET", "/api/v1/csrf", null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        var body = json.readTree(csrf.body());
        token = body.get("token").asText();
        headerName = body.get("headerName").asText();
    }

    @Test
    void criaConsultaEAtualizaLivroComIsbnOpcional() throws Exception {
        var response = enviar("POST", "/api/v1/livros",
                "{\"titulo\":\"  Livro de teste  \",\"autor\":\"  Autor  \",\"isbn\":\"978-0-306-40615-7\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        var livro = json.readTree(response.body());
        String path = "/api/v1/livros/" + livro.get("id").asText();
        assertThat(response.headers().firstValue("Location")).contains(path);
        assertThat(livro.get("titulo").asText()).isEqualTo("Livro de teste");
        assertThat(livro.get("autor").asText()).isEqualTo("Autor");
        assertThat(livro.get("isbn").asText()).isEqualTo("9780306406157");
        assertThat(enviar("GET", path, null).statusCode()).isEqualTo(200);

        var update = enviar("PUT", path, "{\"titulo\":\"Outro título\",\"autor\":\"Outra autora\"}");
        assertThat(update.statusCode()).isEqualTo(200);
        var atualizado = json.readTree(enviar("GET", path, null).body());
        assertThat(atualizado.get("id").asText()).isEqualTo(livro.get("id").asText());
        assertThat(atualizado.get("titulo").asText()).isEqualTo("Outro título");
        assertThat(atualizado.get("isbn").isNull()).isTrue();
    }

    @Test
    void variasCopiasDoMesmoLivroEAtualizacaoDoExemplar() throws Exception {
        String livroId = criarLivro("Livro");
        var response = enviar("POST", "/api/v1/exemplares", exemplar(livroId, "  EX-001  "));
        assertThat(response.statusCode()).isEqualTo(201);
        var copia = json.readTree(response.body());
        String path = "/api/v1/exemplares/" + copia.get("id").asText();
        assertThat(response.headers().firstValue("Location")).contains(path);
        assertThat(copia.get("codigo").asText()).isEqualTo("EX-001");
        assertThat(copia.get("livroId").asText()).isEqualTo(livroId);
        assertThat(copia.get("disponivel").asBoolean()).isTrue();
        assertThat(enviar("POST", "/api/v1/exemplares", exemplar(livroId, "EX-002")).statusCode()).isEqualTo(201);
        assertThat(enviar("PUT", path, exemplar(livroId, "EX-001")).statusCode()).isEqualTo(200);
        String outroLivro = criarLivro("Outro Livro");
        assertThat(enviar("PUT", path, exemplar(outroLivro, "EX-003")).statusCode()).isEqualTo(200);
        var atualizado = json.readTree(enviar("GET", path, null).body());
        assertThat(atualizado.get("id")).isEqualTo(copia.get("id"));
        assertThat(atualizado.get("codigo").asText()).isEqualTo("EX-003");
        assertThat(atualizado.get("livroId").asText()).isEqualTo(outroLivro);
        problema(enviar("PUT", path, exemplar(UUID.randomUUID().toString(), "EX-INVALIDO")), 404);
        var preservado = json.readTree(enviar("GET", path, null).body());
        assertThat(preservado.get("codigo").asText()).isEqualTo("EX-003");
        assertThat(preservado.get("livroId").asText()).isEqualTo(outroLivro);
    }

    @Test
    void codigoUnicoGlobalComNormalizacaoEAtualizacao() throws Exception {
        String livro = criarLivro("Primeiro");
        String outro = criarLivro("Segundo");
        assertThat(enviar("POST", "/api/v1/exemplares", exemplar(livro, "EX-001")).statusCode()).isEqualTo(201);
        problema(enviar("POST", "/api/v1/exemplares", exemplar(outro, " EX-001 ")), 409);
        var copia = enviar("POST", "/api/v1/exemplares", exemplar(outro, "EX-002"));
        String id = json.readTree(copia.body()).get("id").asText();
        problema(enviar("PUT", "/api/v1/exemplares/" + id, exemplar(outro, "EX-001")), 409);
        assertThat(jdbc.queryForObject("SELECT codigo FROM locadora.exemplares WHERE id = ?",
                String.class, UUID.fromString(id))).isEqualTo("EX-002");
    }

    @Test
    void validaDadosELivroExistenteSemGravacaoParcial() throws Exception {
        for (String body : new String[] {"{}", "{", "{\"titulo\":\"Livro\",\"autor\":\"Autor\",\"isbn\":\"9780306406158\"}"}) {
            problema(enviar("POST", "/api/v1/livros", body), 400);
        }
        problema(enviar("POST", "/api/v1/exemplares", "{}"), 400);
        problema(enviar("POST", "/api/v1/exemplares", exemplar(UUID.randomUUID().toString(), "EX-001")), 404);
        problema(enviar("POST", "/api/v1/exemplares", exemplar("id-invalido", "EX-001")), 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.livros", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.exemplares", Integer.class)).isZero();
    }

    @Test
    void paginaRecursosComOrdemEstavelETrataIdsAusentes() throws Exception {
        for (String resource : new String[] {"livros", "exemplares"}) {
            var vazia = json.readTree(enviar("GET", "/api/v1/" + resource, null).body());
            assertThat(vazia.get("items").size()).isZero();
            problema(enviar("GET", "/api/v1/" + resource + "/" + UUID.randomUUID(), null), 404);
            problema(enviar("GET", "/api/v1/" + resource + "/invalido", null), 400);
            for (String parametro : new String[] {"page=-1", "size=0", "size=101", "size=abc"}) {
                problema(enviar("GET", "/api/v1/" + resource + "?" + parametro, null), 400);
            }
        }
        for (int i = 1; i <= 3; i++) {
            String livro = criarLivro("Livro " + i);
            assertThat(enviar("POST", "/api/v1/exemplares", exemplar(livro, "EX-00" + i)).statusCode()).isEqualTo(201);
        }
        for (String resource : new String[] {"livros", "exemplares"}) {
            var pagina = json.readTree(enviar("GET", "/api/v1/" + resource + "?size=2", null).body());
            assertThat(pagina.get("items").size()).isEqualTo(2);
            assertThat(pagina.get("totalElements").asInt()).isEqualTo(3);
            assertThat(pagina.get("totalPages").asInt()).isEqualTo(2);
            assertThat(json.readTree(enviar("GET", "/api/v1/" + resource + "?size=2", null).body())
                    .get("items")).isEqualTo(pagina.get("items"));
            assertThat(json.readTree(enviar("GET", "/api/v1/" + resource + "?page=99", null).body())
                    .get("items").size()).isZero();
        }
        problema(enviar("PUT", "/api/v1/livros/" + UUID.randomUUID(),
                "{\"titulo\":\"Livro\",\"autor\":\"Autor\"}"), 404);
        problema(enviar("PUT", "/api/v1/exemplares/" + UUID.randomUUID(),
                exemplar(UUID.randomUUID().toString(), "EX-001")), 404);
    }

    @Test
    void exigeBasicECsrfNasEscritas() throws Exception {
        for (String resource : new String[] {"livros", "exemplares"}) {
            var anonymous = HttpRequest.newBuilder(uri("/api/v1/" + resource)).GET().build();
            assertThat(client.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            var noToken = HttpRequest.newBuilder(uri("/api/v1/" + resource))
                    .header("Authorization", basic()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            assertThat(client.send(noToken, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void concorrenciaECodigoProtegidoPeloBanco() throws Exception {
        String livro = criarLivro("Livro");
        String body = exemplar(livro, "EX-CONCORRENTE");
        var a = client.sendAsync(requisicao("POST", "/api/v1/exemplares", body), HttpResponse.BodyHandlers.ofString());
        var b = client.sendAsync(requisicao("POST", "/api/v1/exemplares", body), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a, b).join();
        assertThat(new int[] {a.join().statusCode(), b.join().statusCode()}).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.exemplares", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO locadora.exemplares (id, livro_id, codigo) VALUES (?, ?, ?)",
                UUID.randomUUID(), UUID.fromString(livro), "EX-CONCORRENTE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoProtegeVinculoComLivro() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO locadora.exemplares (id, livro_id, codigo) VALUES (?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "EX-SEM-LIVRO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String criarLivro(String titulo) throws Exception {
        var response = enviar("POST", "/api/v1/livros", "{\"titulo\":\"" + titulo + "\",\"autor\":\"Autor\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asText();
    }

    private String exemplar(String livroId, String codigo) {
        return "{\"livroId\":\"" + livroId + "\",\"codigo\":\"" + codigo + "\"}";
    }

    private void problema(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
    }

    private HttpResponse<String> enviar(String method, String path, String body) throws Exception {
        return client.send(requisicao(method, path, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest requisicao(String method, String path, String body) {
        var request = HttpRequest.newBuilder(uri(path)).header("Authorization", basic());
        if (body != null) {
            request.header("Content-Type", "application/json").header(headerName, token);
        }
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private String basic() {
        return "Basic " + Base64.getEncoder().encodeToString(
                "catalog-test-operator:catalog-test-only-password".getBytes(StandardCharsets.UTF_8));
    }
}
