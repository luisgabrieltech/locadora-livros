package br.com.locadora.locacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(LocacoesIT.TempoFixo.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=", "app.operator.username=loan-test-operator",
    "app.operator.password=loan-test-only-password", "app.locacao.diaria=2.00", "app.locacao.multa-diaria=1.00"
})
class LocacoesIT {
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.7-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TempoFixo {
        @Bean
        @Primary
        Clock testeClock() {
            // Já é dia 17 em UTC, mas ainda dia 16 no fuso de negócio.
            return Clock.fixed(Instant.parse("2026-11-17T01:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private HttpClient client;
    private String csrf;
    private String csrfHeader;
    private UUID cliente;
    private UUID cliente2;
    private UUID livro;

    @BeforeEach
    void preparar() throws Exception {
        // Fixtures somente no PostgreSQL descartável desta classe.
        for (String tabela : new String[] {"itens_locacao", "locacoes", "exemplares", "livros", "clientes"}) {
            jdbc.update("DELETE FROM locadora." + tabela);
        }
        cliente = inserirCliente("00000000191");
        cliente2 = inserirCliente("00000000272");
        livro = inserirLivro();
        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var token = enviar("GET", "/api/v1/csrf", null);
        assertThat(token.statusCode()).isEqualTo(200);
        var body = json.readTree(token.body());
        csrf = body.get("token").asText();
        csrfHeader = body.get("headerName").asText();
    }

    @Test
    void filtraHistoricoComPaginacaoSemDuplicarLocacoesComVariosItens() throws Exception {
        var primeira = json.readTree(abrir(cliente, item(copia("A"), HOJE),
                item(copia("B"), HOJE)).body()).get("id").asText();
        var segunda = json.readTree(abrir(cliente, item(copia("C"), HOJE)).body()).get("id").asText();
        assertThat(abrir(cliente2, item(copia("D"), HOJE)).statusCode()).isEqualTo(201);
        String filtro = "/api/v1/locacoes?situacao=ABERTA&clienteId=" + cliente + "&size=1&page=";
        var pagina0 = json.readTree(enviar("GET", filtro + "0", null).body());
        var pagina1 = json.readTree(enviar("GET", filtro + "1", null).body());
        assertThat(pagina0.get("totalElements").asInt()).isEqualTo(2);
        assertThat(pagina1.get("totalElements").asInt()).isEqualTo(2);
        assertThat(pagina0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(pagina1.get("page").asInt()).isEqualTo(1);
        assertThat(pagina0.get("items").size()).isEqualTo(1);
        assertThat(pagina1.get("items").size()).isEqualTo(1);
        assertThat(new String[] {pagina0.get("items").get(0).get("id").asText(),
                pagina1.get("items").get(0).get("id").asText()})
                .containsExactlyInAnyOrder(primeira, segunda);
        assertThat(pagina0.get("items").get(0).get("clienteId").asText()).isEqualTo(cliente.toString());
        assertThat(pagina1.get("items").get(0).get("clienteId").asText()).isEqualTo(cliente.toString());
        assertThat(json.readTree(enviar("GET", "/api/v1/locacoes", null).body())
                .get("totalElements").asInt()).isEqualTo(3);
        for (String query : new String[] {"clienteId=" + UUID.randomUUID(),
                "clienteId=" + cliente2 + "&situacao=ENCERRADA", "page=5&size=1"}) {
            var response = enviar("GET", "/api/v1/locacoes?" + query, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.readTree(response.body()).get("items").size()).isZero();
        }
    }

    @Test
    void validaFiltrosEUsaFusoDeNegocioNaConsulta() throws Exception {
        assertThat(abrir(cliente, item(copia("A"), HOJE)).statusCode()).isEqualTo(201);
        // O Clock está no dia 17 em UTC, mas dia 16 em America/Sao_Paulo.
        var response = enviar("GET", "/api/v1/locacoes?situacao=ATRASADA", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("totalElements").asInt()).isZero();
        for (String query : new String[] {"situacao=INVALIDA", "situacao=aberta", "clienteId=invalido"}) {
            problema(enviar("GET", "/api/v1/locacoes?" + query, null), 400);
        }
        var anonima = HttpRequest.newBuilder(uri("/api/v1/locacoes?situacao=ABERTA")).GET().build();
        assertThat(client.send(anonima, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    @Test
    void abreVariosItensComDataDoServidorEPreservaPrecos() throws Exception {
        var a = copia("A");
        var b = copia("B");
        var response = abrir(cliente, item(a, HOJE), item(b, HOJE.plusDays(4)));
        assertThat(response.statusCode()).isEqualTo(201);
        var body = json.readTree(response.body());
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/locacoes/" + body.get("id").asText());
        assertThat(body.get("dataRetirada").asText()).isEqualTo(HOJE.toString());
        assertThat(body.get("clienteId").asText()).isEqualTo(cliente.toString());
        assertThat(body.get("itens").size()).isEqualTo(2);
        for (JsonNode item : body.get("itens")) {
            assertThat(UUID.fromString(item.get("id").asText())).isNotNull();
            assertThat(new BigDecimal(item.get("valorDiaria").asText())).isEqualByComparingTo("2.00");
            assertThat(new BigDecimal(item.get("valorMultaDiaria").asText())).isEqualByComparingTo("1.00");
        }
        assertThat(jdbc.queryForObject("SELECT min(valor_diaria) FROM locadora.itens_locacao", BigDecimal.class))
                .isEqualByComparingTo("2.00");
        var consultada = enviar("GET", "/api/v1/locacoes/" + body.get("id").asText(), null);
        assertThat(consultada.statusCode()).isEqualTo(200);
        assertThat(json.readTree(consultada.body())).isEqualTo(body);
        assertThat(response.body()).doesNotContain("cpf", "telefone", "email");
    }

    @Test
    void disponibilidadeIndividualEListadaDerivamDaRetirada() throws Exception {
        var a = copia("A");
        var livre = copia("B");
        assertThat(abrir(cliente, item(a, HOJE)).statusCode()).isEqualTo(201);
        assertThat(disponivel(a)).isFalse();
        assertThat(disponivel(livre)).isTrue();
        var listagem = json.readTree(enviar("GET", "/api/v1/exemplares", null).body()).get("items");
        for (JsonNode copia : listagem) {
            assertThat(copia.get("disponivel").asBoolean())
                    .isEqualTo(copia.get("id").asText().equals(livre.toString()));
        }
    }

    @Test
    void conflitosOuItensAusentesNaoDeixamLocacaoParcial() throws Exception {
        var ocupado = copia("A");
        var livre = copia("B");
        assertThat(abrir(cliente, item(ocupado, HOJE)).statusCode()).isEqualTo(201);
        problema(abrir(cliente2, item(livre, HOJE), item(ocupado, HOJE)), 409);
        problema(abrir(cliente2, item(livre, HOJE), item(UUID.randomUUID(), HOJE)), 404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.locacoes", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.itens_locacao", Integer.class)).isEqualTo(1);
        assertThat(disponivel(livre)).isTrue();
    }

    @Test
    void validaEntradaENaoAceitaPrecosOuRetiradaDoCliente() throws Exception {
        var a = copia("A");
        String valido = dados(cliente, item(a, HOJE));
        for (String payload : new String[] {
                "{}", "{", dados(cliente), dados(cliente, item(a, HOJE.minusDays(1))),
                dados(cliente, item(a, HOJE), item(a, HOJE.plusDays(1))),
                "{\"clienteId\":\"" + cliente + "\",\"itens\":[null]}",
                valido.substring(0, valido.length() - 1) + ",\"dataRetirada\":\"2020-01-01\"}",
                valido.substring(0, valido.length() - 1) + ",\"valorDiaria\":0.01}",
                valido.replace("\"dataPrevistaDevolucao\"", "\"valorDiaria\":0.01,\"dataPrevistaDevolucao\""),
                valido.replace(HOJE.toString(), "2026-02-30")}) {
            problema(enviar("POST", "/api/v1/locacoes", payload), 400);
        }
        problema(abrir(UUID.randomUUID(), item(a, HOJE)), 404);
        problema(enviar("GET", "/api/v1/locacoes/" + UUID.randomUUID(), null), 404);
        problema(enviar("GET", "/api/v1/locacoes/id-invalido", null), 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.locacoes", Integer.class)).isZero();
        assertThat(disponivel(a)).isTrue();
    }

    @Test
    void rejeitaPrazoForaDaFaixaDoBancoSemPersistir() throws Exception {
        var a = copia("A");
        problema(abrir(cliente, item(a, LocalDate.of(5874898, 1, 1))), 400);
        problema(abrir(cliente, item(a, LocalDate.of(10000000, 1, 1))), 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.locacoes", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.itens_locacao", Integer.class)).isZero();
        assertThat(disponivel(a)).isTrue();
        assertThat(abrir(cliente, item(a, LocalDate.of(5874897, 12, 31))).statusCode()).isEqualTo(201);
    }

    @Test
    void clienteComItensNoPrazoPodeRetirarMasAtrasadoNao() throws Exception {
        var a = copia("A");
        assertThat(abrir(cliente, item(a, HOJE)).statusCode()).isEqualTo(201);
        assertThat(abrir(cliente, item(copia("B"), HOJE.plusDays(1))).statusCode()).isEqualTo(201);
        // Histórico de ontem criado por fixture, sem liberar lançamentos retroativos pela API.
        jdbc.update("UPDATE locadora.locacoes SET data_retirada = ?", HOJE.minusDays(2));
        jdbc.update("UPDATE locadora.itens_locacao SET data_prevista_devolucao = ? WHERE exemplar_id = ?",
                HOJE.minusDays(1), a);
        problema(abrir(cliente, item(copia("C"), HOJE)), 409);
    }

    @Test
    void dividaDeDevolvidoBloqueiaAteQuitacaoSemManterCopiaIndisponivel() throws Exception {
        var a = copia("A");
        var abertura = abrir(cliente, item(a, HOJE));
        assertThat(abertura.statusCode()).isEqualTo(201);
        var locacao = json.readTree(abertura.body());
        assertThat(enviar("POST", "/api/v1/locacoes/" + locacao.get("id").asText() + "/devolucoes",
                "{\"itens\":[\"" + locacao.get("itens").get(0).get("id").asText() + "\"]}")
                .statusCode()).isEqualTo(200);
        assertThat(disponivel(a)).isTrue();
        var b = copia("B");
        problema(abrir(cliente, item(b, HOJE)), 409);
        assertThat(enviar("POST", "/api/v1/locacoes/" + locacao.get("id").asText() + "/pagamentos",
                "{\"itens\":[\"" + locacao.get("itens").get(0).get("id").asText() + "\"]}")
                .statusCode()).isEqualTo(200);
        assertThat(abrir(cliente, item(b, HOJE)).statusCode()).isEqualTo(201);
    }

    @Test
    void historicoImpedeTrocaDoLivroMesmoDepoisDeDevolucao() throws Exception {
        var a = copia("A");
        var abertura = abrir(cliente, item(a, HOJE));
        assertThat(abertura.statusCode()).isEqualTo(201);
        var locacao = json.readTree(abertura.body());
        UUID outroLivro = inserirLivro();
        problema(enviar("PUT", "/api/v1/exemplares/" + a, exemplar(outroLivro, "A")), 409);
        assertThat(enviar("POST", "/api/v1/locacoes/" + locacao.get("id").asText() + "/devolucoes",
                "{\"itens\":[\"" + locacao.get("itens").get(0).get("id").asText() + "\"]}")
                .statusCode()).isEqualTo(200);
        problema(enviar("PUT", "/api/v1/exemplares/" + a, exemplar(outroLivro, "A")), 409);
        assertThat(enviar("PUT", "/api/v1/exemplares/" + a, exemplar(livro, "NOVO-CODIGO")).statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT livro_id FROM locadora.exemplares WHERE id = ?", UUID.class, a))
                .isEqualTo(livro);
    }

    @Test
    void listaPaginadaEProtecaoDeAcesso() throws Exception {
        var vazia = json.readTree(enviar("GET", "/api/v1/locacoes", null).body());
        assertThat(vazia.get("items").size()).isZero();
        assertThat(abrir(cliente, item(copia("A"), HOJE)).statusCode()).isEqualTo(201);
        assertThat(abrir(cliente, item(copia("B"), HOJE.plusDays(1))).statusCode()).isEqualTo(201);
        var pagina = json.readTree(enviar("GET", "/api/v1/locacoes?size=1", null).body());
        assertThat(pagina.get("items").size()).isEqualTo(1);
        assertThat(pagina.get("totalElements").asInt()).isEqualTo(2);
        assertThat(pagina.get("totalPages").asInt()).isEqualTo(2);
        for (String parametros : new String[] {"page=-1", "size=0", "size=101", "page=abc"}) {
            problema(enviar("GET", "/api/v1/locacoes?" + parametros, null), 400);
        }
        var anonima = HttpRequest.newBuilder(uri("/api/v1/locacoes")).GET().build();
        assertThat(client.send(anonima, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        var semToken = HttpRequest.newBuilder(uri("/api/v1/locacoes")).header("Authorization", basic())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertThat(client.send(semToken, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void retiradasSimultaneasDaMesmaCopiaGeramUmaCriacaoEUmConflito() throws Exception {
        var a = copia("A");
        concorrencia(dados(cliente, item(a, HOJE)), dados(cliente2, item(a, HOJE)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.itens_locacao", Integer.class)).isEqualTo(1);
    }

    @Test
    void listasInvertidasNaoCausamRetiradasParciaisOuDeadlock() throws Exception {
        var a = copia("A");
        var b = copia("B");
        concorrencia(dados(cliente, item(a, HOJE), item(b, HOJE)),
                dados(cliente2, item(b, HOJE), item(a, HOJE)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.locacoes", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM locadora.itens_locacao", Integer.class)).isEqualTo(2);
    }

    @Test
    void bancoImpedeSegundaRetiradaAtivaDaMesmaCopia() throws Exception {
        var a = copia("A");
        assertThat(abrir(cliente, item(a, HOJE)).statusCode()).isEqualTo(201);
        var outra = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.locacoes(id, cliente_id, data_retirada) VALUES (?, ?, ?)",
                outra, cliente2, HOJE);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO locadora.itens_locacao
                (id, locacao_id, exemplar_id, data_prevista_devolucao, valor_diaria, valor_multa_diaria)
                VALUES (?, ?, ?, ?, 2.00, 1.00)
                """, UUID.randomUUID(), outra, a, HOJE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void concorrencia(String corpoA, String corpoB) {
        var a = client.sendAsync(request("POST", "/api/v1/locacoes", corpoA), HttpResponse.BodyHandlers.ofString());
        var b = client.sendAsync(request("POST", "/api/v1/locacoes", corpoB), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a, b).orTimeout(30, TimeUnit.SECONDS).join();
        assertThat(new int[] {a.join().statusCode(), b.join().statusCode()}).containsExactlyInAnyOrder(201, 409);
    }

    private UUID inserirCliente(String cpf) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.clientes(id, nome, telefone, cpf) VALUES (?, ?, ?, ?)",
                id, "Cliente fixture", "11999990000", cpf);
        return id;
    }

    private UUID inserirLivro() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.livros(id, titulo, autor) VALUES (?, ?, ?)", id, "Livro fixture", "Autor");
        return id;
    }

    private UUID copia(String codigo) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.exemplares(id, livro_id, codigo) VALUES (?, ?, ?)", id, livro, codigo);
        return id;
    }

    private boolean disponivel(UUID id) throws Exception {
        return json.readTree(enviar("GET", "/api/v1/exemplares/" + id, null).body()).get("disponivel").asBoolean();
    }

    private String exemplar(UUID livroId, String codigo) {
        return "{\"livroId\":\"" + livroId + "\",\"codigo\":\"" + codigo + "\"}";
    }

    private HttpResponse<String> abrir(UUID id, String... itens) throws Exception {
        return enviar("POST", "/api/v1/locacoes", dados(id, itens));
    }

    private String dados(UUID id, String... itens) {
        return "{\"clienteId\":\"" + id + "\",\"itens\":[" + Arrays.stream(itens).collect(Collectors.joining(",")) + "]}";
    }

    private String item(UUID id, LocalDate prazo) {
        return "{\"exemplarId\":\"" + id + "\",\"dataPrevistaDevolucao\":\"" + prazo + "\"}";
    }

    private void problema(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
    }

    private HttpResponse<String> enviar(String method, String path, String body) throws Exception {
        return client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String body) {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30)).header("Authorization", basic());
        if (body != null) {
            request.header("Content-Type", "application/json").header(csrfHeader, csrf);
        }
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private String basic() {
        return "Basic " + Base64.getEncoder().encodeToString(
                "loan-test-operator:loan-test-only-password".getBytes(StandardCharsets.UTF_8));
    }
}
