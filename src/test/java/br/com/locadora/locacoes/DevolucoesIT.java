package br.com.locadora.locacoes;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.concurrent.atomic.AtomicReference;
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
@Import(DevolucoesIT.Tempo.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=", "app.operator.username=return-test-operator",
    "app.operator.password=return-test-only-password", "app.locacao.diaria=2.00", "app.locacao.multa-diaria=1.00"
})
class DevolucoesIT {
    private static final LocalDate RETIRADA = LocalDate.of(2026, 11, 10);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.7-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Tempo {
        @Bean
        @Primary
        RelogioMutavel clockTeste() {
            return new RelogioMutavel(new AtomicReference<>(Instant.parse("2026-11-10T12:00:00Z")),
                    ZoneId.of("America/Sao_Paulo"));
        }
    }

    static final class RelogioMutavel extends Clock {
        private final AtomicReference<Instant> instante;
        private final ZoneId zona;

        RelogioMutavel(AtomicReference<Instant> instante, ZoneId zona) {
            this.instante = instante;
            this.zona = zona;
        }

        void data(LocalDate dia) {
            instante.set(dia.atTime(12, 0).atZone(zona).toInstant());
        }

        @Override
        public ZoneId getZone() { return zona; }

        @Override
        public Clock withZone(ZoneId zona) { return new RelogioMutavel(instante, zona); }

        @Override
        public Instant instant() { return instante.get(); }
    }

    @Autowired
    private RelogioMutavel clock;
    @Autowired
    private JdbcTemplate jdbc;
    @LocalServerPort
    private int port;

    private final JsonMapper json = JsonMapper.builder().build();
    private HttpClient client;
    private String csrf;
    private String csrfHeader;
    private UUID cliente;
    private UUID cliente2;
    private UUID livro;

    @BeforeEach
    void preparar() throws Exception {
        clock.data(RETIRADA);
        // Exclusivamente no banco temporário desta classe.
        for (String tabela : new String[] {"itens_locacao", "locacoes", "exemplares", "livros", "clientes"}) {
            jdbc.update("DELETE FROM locadora." + tabela);
        }
        cliente = inserirCliente("00000000191");
        cliente2 = inserirCliente("00000000272");
        livro = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.livros(id, titulo, autor) VALUES (?, ?, ?)", livro, "Livro", "Autor");
        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var token = enviar("GET", "/api/v1/csrf", null);
        assertThat(token.statusCode()).isEqualTo(200);
        var body = json.readTree(token.body());
        csrf = body.get("token").asText();
        csrfHeader = body.get("headerName").asText();
    }

    @Test
    void devolveParteDepoisRestanteMesmoComClienteBloqueado() throws Exception {
        var a = copia("A");
        var b = copia("B");
        var c = copia("C");
        var locacao = abrir(cliente, a, b, c);
        String locacaoId = locacao.get("id").asText();
        clock.data(RETIRADA.plusDays(1));
        var response = devolver(locacaoId, idItem(locacao, a));
        assertThat(response.statusCode()).isEqualTo(200);
        var resultado = json.readTree(response.body()).get("itens");
        assertThat(resultado.size()).isEqualTo(1);
        assertThat(new BigDecimal(resultado.get(0).get("valorTotal").asText())).isEqualByComparingTo("2.00");
        assertThat(disponivel(a)).isTrue();
        assertThat(disponivel(b)).isFalse();
        assertThat(disponivel(c)).isFalse();

        var detalhe = consultar(locacaoId);
        assertThat(item(detalhe, a).get("dataDevolucao").asText()).isEqualTo("2026-11-11");
        assertThat(item(detalhe, a).get("dataPagamento").isNull()).isTrue();
        assertThat(item(detalhe, b).get("dataDevolucao").isNull()).isTrue();
        clock.data(RETIRADA.plusDays(4));
        var restante = devolver(locacaoId, idItem(locacao, b), idItem(locacao, c));
        assertThat(restante.statusCode()).isEqualTo(200);
        for (JsonNode resultadoItem : json.readTree(restante.body()).get("itens")) {
            assertThat(resultadoItem.get("diasCobrados").asLong()).isEqualTo(4);
            assertThat(resultadoItem.get("diasAtraso").asLong()).isEqualTo(2);
            assertThat(new BigDecimal(resultadoItem.get("valorTotal").asText())).isEqualByComparingTo("10.00");
        }
        assertThat(disponivel(b)).isTrue();
        assertThat(disponivel(c)).isTrue();
    }

    @Test
    void usaTarifasHistoricasEFixaValorSemNovasMultas() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        String id = idItem(locacao, a);
        // Simular tarifa de contrato anterior, diferente dos 2/1 da configuração atual.
        jdbc.update("UPDATE locadora.itens_locacao SET valor_diaria = 3.00, valor_multa_diaria = 2.00 WHERE id = ?",
                UUID.fromString(id));
        clock.data(RETIRADA.plusDays(4));
        var response = devolver(locacao.get("id").asText(), id);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new BigDecimal(json.readTree(response.body()).get("itens").get(0).get("valorTotal").asText()))
                .isEqualByComparingTo("16.00");
        clock.data(RETIRADA.plusDays(20));
        problema(devolver(locacao.get("id").asText(), id), 409);
        var preservado = item(consultar(locacao.get("id").asText()), a);
        assertThat(preservado.get("dataDevolucao").asText()).isEqualTo("2026-11-14");
        assertThat(new BigDecimal(preservado.get("valorTotal").asText())).isEqualByComparingTo("16.00");
    }

    @Test
    void devolucaoGeraDividaMasLiberaCopiaParaOutroCliente() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        String locacaoId = locacao.get("id").asText();
        String itemId = idItem(locacao, a);
        assertThat(devolver(locacaoId, itemId).statusCode()).isEqualTo(200);
        var novo = copia("B");
        problema(enviar("POST", "/api/v1/locacoes", corpoAbertura(cliente, novo)), 409);
        var outraRetirada = enviar("POST", "/api/v1/locacoes", corpoAbertura(cliente2, a));
        assertThat(outraRetirada.statusCode()).isEqualTo(201);
        problema(devolver(locacaoId, itemId), 409);
        assertThat(disponivel(a)).isFalse();
        var outraLocacao = json.readTree(outraRetirada.body());
        assertThat(item(consultar(outraLocacao.get("id").asText()), a).get("dataDevolucao").isNull()).isTrue();
    }

    @Test
    void validaListaIdsECamposControladosPeloServidor() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        String locacaoId = locacao.get("id").asText();
        String id = idItem(locacao, a);
        String valido = corpoDevolucao(id);
        for (String body : new String[] {
                "{}", "{", "{\"itens\":[]}", "{\"itens\":[null]}", corpoDevolucao("invalido"),
                corpoDevolucao(id, id),
                valido.substring(0, valido.length() - 1) + ",\"dataDevolucao\":\"2026-11-10\"}",
                valido.substring(0, valido.length() - 1) + ",\"valorTotal\":0.01}",
                valido.substring(0, valido.length() - 1) + ",\"pago\":true}"}) {
            problema(enviar("POST", caminho(locacaoId), body), 400);
        }
        problema(devolver(UUID.randomUUID().toString(), id), 404);
        problema(devolver("id-invalido", id), 400);
        assertThat(item(consultar(locacaoId), a).get("dataDevolucao").isNull()).isTrue();
    }

    @Test
    void itemInexistenteOuDeOutraLocacaoNaoDevolveParteDoLote() throws Exception {
        var a = copia("A");
        var b = copia("B");
        var primeira = abrir(cliente, a);
        var segunda = abrir(cliente, b);
        String primeiraId = primeira.get("id").asText();
        String aId = idItem(primeira, a);
        problema(devolver(primeiraId, aId, UUID.randomUUID().toString()), 404);
        problema(devolver(primeiraId, aId, idItem(segunda, b)), 404);
        assertThat(disponivel(a)).isFalse();
        assertThat(disponivel(b)).isFalse();
        assertThat(item(consultar(primeiraId), a).get("valorTotal").isNull()).isTrue();
    }

    @Test
    void repeticaoEmLoteNaoDevolveOsItensAindaAtivos() throws Exception {
        var a = copia("A");
        var b = copia("B");
        var locacao = abrir(cliente, a, b);
        String locacaoId = locacao.get("id").asText();
        String aId = idItem(locacao, a);
        assertThat(devolver(locacaoId, aId).statusCode()).isEqualTo(200);
        problema(devolver(locacaoId, aId, idItem(locacao, b)), 409);
        assertThat(disponivel(b)).isFalse();
        assertThat(item(consultar(locacaoId), b).get("valorTotal").isNull()).isTrue();
    }

    @Test
    void devolucoesConcorrentesDoMesmoItemNaoRecalculam() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        concorrencia(locacao.get("id").asText(), corpoDevolucao(idItem(locacao, a)),
                corpoDevolucao(idItem(locacao, a)));
        assertThat(disponivel(a)).isTrue();
        assertThat(jdbc.queryForObject("SELECT valor_total FROM locadora.itens_locacao", BigDecimal.class))
                .isEqualByComparingTo("2.00");
    }

    @Test
    void lotesConcorrentesComSobreposicaoNaoDevolvemItensParcialmente() throws Exception {
        var a = copia("A");
        var b = copia("B");
        var c = copia("C");
        var locacao = abrir(cliente, a, b, c);
        concorrencia(locacao.get("id").asText(), corpoDevolucao(idItem(locacao, a), idItem(locacao, b)),
                corpoDevolucao(idItem(locacao, b), idItem(locacao, c)));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM locadora.itens_locacao WHERE data_devolucao IS NOT NULL", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void retiradaConcorrenteComDevolucaoNaoPerdeEstadoDaNovaRetirada() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        String path = caminho(locacao.get("id").asText());
        var devolucao = client.sendAsync(request("POST", path, corpoDevolucao(idItem(locacao, a))),
                HttpResponse.BodyHandlers.ofString());
        var retirada = client.sendAsync(request("POST", "/api/v1/locacoes", corpoAbertura(cliente2, a)),
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(devolucao, retirada).orTimeout(30, TimeUnit.SECONDS).join();
        assertThat(devolucao.join().statusCode()).isEqualTo(200);
        assertThat(retirada.join().statusCode()).isIn(201, 409);
        if (retirada.join().statusCode() == 409) {
            assertThat(enviar("POST", "/api/v1/locacoes", corpoAbertura(cliente2, a)).statusCode()).isEqualTo(201);
        }
        problema(devolver(locacao.get("id").asText(), idItem(locacao, a)), 409);
        assertThat(disponivel(a)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM locadora.itens_locacao WHERE data_devolucao IS NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void excederLimiteMonetarioImpedeTodoOLote() throws Exception {
        var a = copia("A");
        var b = copia("B");
        var locacao = abrir(cliente, a, b);
        jdbc.update("UPDATE locadora.itens_locacao SET valor_diaria = 9999999999.99 WHERE exemplar_id = ?", b);
        clock.data(RETIRADA.plusDays(2));
        problema(devolver(locacao.get("id").asText(), idItem(locacao, a), idItem(locacao, b)), 400);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM locadora.itens_locacao WHERE data_devolucao IS NOT NULL", Integer.class))
                .isZero();
        assertThat(disponivel(a)).isFalse();
        assertThat(disponivel(b)).isFalse();
    }

    @Test
    void exigeAutenticacaoECsrf() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        String path = caminho(locacao.get("id").asText());
        String body = corpoDevolucao(idItem(locacao, a));
        var anonima = HttpRequest.newBuilder(uri(path)).header(csrfHeader, csrf)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertThat(client.send(anonima, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        var semCsrf = HttpRequest.newBuilder(uri(path)).header("Authorization", basic())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertThat(client.send(semCsrf, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(disponivel(a)).isFalse();
    }

    private void concorrencia(String id, String bodyA, String bodyB) {
        var a = client.sendAsync(request("POST", caminho(id), bodyA), HttpResponse.BodyHandlers.ofString());
        var b = client.sendAsync(request("POST", caminho(id), bodyB), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a, b).orTimeout(30, TimeUnit.SECONDS).join();
        assertThat(new int[] {a.join().statusCode(), b.join().statusCode()}).containsExactlyInAnyOrder(200, 409);
    }

    private JsonNode abrir(UUID clienteId, UUID... copias) throws Exception {
        var response = enviar("POST", "/api/v1/locacoes", corpoAbertura(clienteId, copias));
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body());
    }

    private String corpoAbertura(UUID clienteId, UUID... copias) {
        String itens = Arrays.stream(copias).map(id -> "{\"exemplarId\":\"" + id
                + "\",\"dataPrevistaDevolucao\":\"" + RETIRADA.plusDays(2) + "\"}").collect(Collectors.joining(","));
        return "{\"clienteId\":\"" + clienteId + "\",\"itens\":[" + itens + "]}";
    }

    private String corpoDevolucao(String... ids) {
        return "{\"itens\":[" + Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
    }

    private HttpResponse<String> devolver(String id, String... itens) throws Exception {
        return enviar("POST", caminho(id), corpoDevolucao(itens));
    }

    private JsonNode consultar(String id) throws Exception {
        var response = enviar("GET", "/api/v1/locacoes/" + id, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private JsonNode item(JsonNode locacao, UUID exemplar) {
        for (JsonNode item : locacao.get("itens")) {
            if (item.get("exemplarId").asText().equals(exemplar.toString())) {
                return item;
            }
        }
        throw new AssertionError("Item não encontrado na resposta");
    }

    private String idItem(JsonNode locacao, UUID exemplar) {
        return item(locacao, exemplar).get("id").asText();
    }

    private boolean disponivel(UUID copia) throws Exception {
        return json.readTree(enviar("GET", "/api/v1/exemplares/" + copia, null).body()).get("disponivel").asBoolean();
    }

    private String caminho(String id) { return "/api/v1/locacoes/" + id + "/devolucoes"; }

    private UUID inserirCliente(String cpf) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.clientes (id, nome, telefone, cpf) VALUES (?, ?, ?, ?)",
                id, "Cliente fixture", "11999990000", cpf);
        return id;
    }

    private UUID copia(String codigo) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.exemplares(id, livro_id, codigo) VALUES (?, ?, ?)", id, livro, codigo);
        return id;
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

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    private String basic() {
        return "Basic " + Base64.getEncoder().encodeToString(
                "return-test-operator:return-test-only-password".getBytes(StandardCharsets.UTF_8));
    }
}
