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
import java.time.Duration;
import java.time.LocalDate;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
    "spring.config.import=", "app.operator.username=payment-test-operator",
    "app.operator.password=payment-test-only-password", "app.locacao.diaria=2.00", "app.locacao.multa-diaria=1.00"
})
class PagamentosIT {
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 10);
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.7-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DevolucoesIT.RelogioMutavel clock;
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
        clock.data(INICIO);
        // Apenas o banco temporário desta classe.
        for (String tabela : new String[] {"itens_locacao", "locacoes", "exemplares", "livros", "clientes"}) {
            jdbc.update("DELETE FROM locadora." + tabela);
        }
        cliente = inserirCliente("00000000191");
        cliente2 = inserirCliente("00000000272");
        livro = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.livros(id, titulo, autor) VALUES (?, ?, ?)", livro, "Livro", "Autor");
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var response = enviar("GET", "/api/v1/csrf", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var token = json.readTree(response.body());
        csrfHeader = token.get("headerName").asText();
        csrf = token.get("token").asText();
    }

    @Test
    void consultasDistinguemAtrasoDividaEEncerramentoComDevolucaoParcial() throws Exception {
        UUID a = copia("A"), b = copia("B"), c = copia("C");
        var locacao = abrir(cliente, a, b, c);
        String id = locacao.get("id").asText();
        jdbc.update("UPDATE locadora.itens_locacao SET data_prevista_devolucao = ? WHERE exemplar_id = ?",
                INICIO.plusDays(5), c);
        assertResumo(id, false, false, false);
        assertFiltro("ABERTA", 1);
        assertFiltro("ATRASADA", 0);
        clock.data(INICIO.plusDays(2));
        assertFiltro("ATRASADA", 0);
        clock.data(INICIO.plusDays(3));
        assertFiltro("ATRASADA", 1);
        assertResumo(id, false, true, false);
        devolver(id, itemId(locacao, a));
        assertResumo(id, false, true, true);
        assertFiltro("ATRASADA", 1);
        assertFiltro("PAGAMENTO_PENDENTE", 1);
        devolver(id, itemId(locacao, b));
        assertResumo(id, false, false, true);
        assertFiltro("ATRASADA", 0);
        assertFiltro("ABERTA", 1);
        assertFiltro("ENCERRADA", 0);
        devolver(id, itemId(locacao, c));
        assertThat(pagar(id, itemId(locacao, a)).statusCode()).isEqualTo(200);
        assertFiltro("PAGAMENTO_PENDENTE", 1);
        assertThat(pagar(id, itemId(locacao, b), itemId(locacao, c)).statusCode()).isEqualTo(200);
        assertResumo(id, true, false, false);
        assertFiltro("ABERTA", 0);
        assertFiltro("PAGAMENTO_PENDENTE", 0);
        assertFiltro("ENCERRADA", 1);
    }

    private void assertFiltro(String situacao, int total) throws Exception {
        var response = enviar("GET", "/api/v1/locacoes?situacao=" + situacao, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var pagina = json.readTree(response.body());
        assertThat(pagina.get("totalElements").asInt()).isEqualTo(total);
        assertThat(pagina.get("items").size()).isEqualTo(total);
    }

    private void assertResumo(String id, boolean encerrada, boolean atrasada, boolean pendente) throws Exception {
        var response = enviar("GET", "/api/v1/locacoes?clienteId=" + cliente, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("cpf", "telefone", "email");
        var resumo = json.readTree(response.body()).get("items").get(0);
        assertThat(resumo.get("id").asText()).isEqualTo(id);
        assertThat(resumo.get("encerrada").asBoolean()).isEqualTo(encerrada);
        assertThat(resumo.get("atrasada").asBoolean()).isEqualTo(atrasada);
        assertThat(resumo.get("pagamentoPendente").asBoolean()).isEqualTo(pendente);
    }

    @Test
    void pagaAlgunsItensMantemDividaDepoisEncerraELiberaCliente() throws Exception {
        UUID a = copia("A"), b = copia("B"), c = copia("C");
        var locacao = abrir(cliente, a, b, c);
        String id = locacao.get("id").asText();
        String aId = itemId(locacao, a), bId = itemId(locacao, b), cId = itemId(locacao, c);
        devolver(id, aId, bId, cId);
        assertThat(consultar(id).get("encerrada").asBoolean()).isFalse();

        clock.data(INICIO.plusDays(10));
        var primeiro = pagar(id, aId);
        assertThat(primeiro.statusCode()).isEqualTo(200);
        var recibo = json.readTree(primeiro.body());
        assertThat(recibo.get("dataPagamento").asText()).isEqualTo("2026-11-20");
        assertThat(new BigDecimal(recibo.get("valorTotalPago").asText())).isEqualByComparingTo("2.00");
        assertThat(recibo.get("locacaoEncerrada").asBoolean()).isFalse();
        var detalhe = consultar(id);
        assertThat(item(detalhe, a).get("dataPagamento").asText()).isEqualTo("2026-11-20");
        assertThat(item(detalhe, b).get("dataPagamento").isNull()).isTrue();
        assertThat(new BigDecimal(item(detalhe, a).get("valorTotal").asText())).isEqualByComparingTo("2.00");
        problema(enviar("POST", "/api/v1/locacoes", abertura(cliente, a)), 409);

        var restante = pagar(id, bId, cId);
        assertThat(restante.statusCode()).isEqualTo(200);
        assertThat(new BigDecimal(json.readTree(restante.body()).get("valorTotalPago").asText()))
                .isEqualByComparingTo("4.00");
        assertThat(json.readTree(restante.body()).get("locacaoEncerrada").asBoolean()).isTrue();
        assertThat(consultar(id).get("encerrada").asBoolean()).isTrue();
        assertThat(enviar("POST", "/api/v1/locacoes", abertura(cliente, a)).statusCode()).isEqualTo(201);
    }

    @Test
    void quitarDividaNaoLiberaClienteComOutroExemplarAtrasado() throws Exception {
        UUID a = copia("A"), b = copia("B");
        var locacao = abrir(cliente, a, b);
        String id = locacao.get("id").asText();
        devolver(id, itemId(locacao, a));
        clock.data(INICIO.plusDays(5));
        assertThat(pagar(id, itemId(locacao, a)).statusCode()).isEqualTo(200);
        problema(enviar("POST", "/api/v1/locacoes", abertura(cliente, copia("C"))), 409);
        assertThat(consultar(id).get("encerrada").asBoolean()).isFalse();
        devolver(id, itemId(locacao, b));
        assertThat(pagar(id, itemId(locacao, b)).statusCode()).isEqualTo(200);
        assertThat(consultar(id).get("encerrada").asBoolean()).isTrue();
        assertThat(enviar("POST", "/api/v1/locacoes", abertura(cliente, a)).statusCode()).isEqualTo(201);
    }

    @Test
    void precisaQuitarDividasDeTodasAsLocacoes() throws Exception {
        UUID a = copia("A"), b = copia("B");
        var primeira = abrir(cliente, a);
        var segunda = abrir(cliente, b);
        String id1 = primeira.get("id").asText(), id2 = segunda.get("id").asText();
        devolver(id1, itemId(primeira, a));
        devolver(id2, itemId(segunda, b));
        assertThat(pagar(id1, itemId(primeira, a)).statusCode()).isEqualTo(200);
        assertThat(consultar(id1).get("encerrada").asBoolean()).isTrue();
        problema(enviar("POST", "/api/v1/locacoes", abertura(cliente, a)), 409);
        assertThat(pagar(id2, itemId(segunda, b)).statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/api/v1/locacoes", abertura(cliente, a)).statusCode()).isEqualTo(201);
    }

    @Test
    void naoPagaItemEmprestadoNemParteDeLoteInvalido() throws Exception {
        UUID a = copia("A"), b = copia("B");
        var locacao = abrir(cliente, a, b);
        String id = locacao.get("id").asText();
        devolver(id, itemId(locacao, a));
        problema(pagar(id, itemId(locacao, a), itemId(locacao, b)), 409);
        assertThat(item(consultar(id), a).get("dataPagamento").isNull()).isTrue();
        assertThat(item(consultar(id), b).get("dataDevolucao").isNull()).isTrue();
    }

    @Test
    void repeticaoPreservaPagamentoAnteriorENaoQuitaRestanteDoLote() throws Exception {
        UUID a = copia("A"), b = copia("B");
        var locacao = abrir(cliente, a, b);
        String id = locacao.get("id").asText();
        devolver(id, itemId(locacao, a), itemId(locacao, b));
        assertThat(pagar(id, itemId(locacao, a)).statusCode()).isEqualTo(200);
        clock.data(INICIO.plusDays(1));
        problema(pagar(id, itemId(locacao, a), itemId(locacao, b)), 409);
        var detalhe = consultar(id);
        assertThat(item(detalhe, a).get("dataPagamento").asText()).isEqualTo(INICIO.toString());
        assertThat(item(detalhe, b).get("dataPagamento").isNull()).isTrue();
    }

    @Test
    void validaListaIdsENaoAceitaValoresDatasOuParcelamento() throws Exception {
        UUID a = copia("A"), b = copia("B");
        var primeira = abrir(cliente, a);
        var segunda = abrir(cliente, b);
        String id = primeira.get("id").asText(), aId = itemId(primeira, a);
        devolver(id, aId);
        String valido = corpo(aId);
        for (String body : new String[] {"{}", "{", corpo(), "{\"itens\":[null]}", corpo("invalido"),
                corpo(aId, aId),
                valido.substring(0, valido.length() - 1) + ",\"valorPago\":0.01}",
                valido.substring(0, valido.length() - 1) + ",\"dataPagamento\":\"2020-01-01\"}",
                valido.substring(0, valido.length() - 1) + ",\"parcelas\":2}"}) {
            problema(enviar("POST", caminho(id), body), 400);
        }
        problema(pagar(id, aId, UUID.randomUUID().toString()), 404);
        problema(pagar(id, aId, itemId(segunda, b)), 404);
        problema(pagar(UUID.randomUUID().toString(), aId), 404);
        problema(pagar("invalido", aId), 400);
        assertThat(item(consultar(id), a).get("dataPagamento").isNull()).isTrue();
    }

    @Test
    void pagamentosConcorrentesDoMesmoItemRegistramUmaUnicaQuitacao() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        String id = locacao.get("id").asText(), item = itemId(locacao, a);
        devolver(id, item);
        concorrencia(id, corpo(item), corpo(item));
        assertThat(consultar(id).get("encerrada").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM locadora.itens_locacao WHERE data_pagamento IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void lotesConcorrentesSobrepostosNaoQuitamRestanteParcialmente() throws Exception {
        UUID a = copia("A"), b = copia("B"), c = copia("C");
        var locacao = abrir(cliente, a, b, c);
        String id = locacao.get("id").asText();
        devolver(id, itemId(locacao, a), itemId(locacao, b), itemId(locacao, c));
        concorrencia(id, corpo(itemId(locacao, a), itemId(locacao, b)),
                corpo(itemId(locacao, b), itemId(locacao, c)));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM locadora.itens_locacao WHERE data_pagamento IS NOT NULL", Integer.class))
                .isEqualTo(2);
        assertThat(consultar(id).get("encerrada").asBoolean()).isFalse();
    }

    @Test
    void pagamentoConcorrenteComRetiradaRespeitaBloqueioDoCliente() throws Exception {
        UUID a = copia("A"), b = copia("B");
        var locacao = abrir(cliente, a);
        String id = locacao.get("id").asText();
        devolver(id, itemId(locacao, a));
        var pagamento = client.sendAsync(request("POST", caminho(id), corpo(itemId(locacao, a))),
                HttpResponse.BodyHandlers.ofString());
        var retirada = client.sendAsync(request("POST", "/api/v1/locacoes", abertura(cliente, b)),
                HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(pagamento, retirada).orTimeout(30, TimeUnit.SECONDS).join();
        assertThat(pagamento.join().statusCode()).isEqualTo(200);
        assertThat(retirada.join().statusCode()).isIn(201, 409);
        if (retirada.join().statusCode() == 409) {
            assertThat(enviar("POST", "/api/v1/locacoes", abertura(cliente, b)).statusCode()).isEqualTo(201);
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM locadora.itens_locacao WHERE data_devolucao IS NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void pagarEmprestimoAntigoNaoAlteraNovaRetiradaDaMesmaCopia() throws Exception {
        var a = copia("A");
        var antiga = abrir(cliente, a);
        String id = antiga.get("id").asText(), itemId = itemId(antiga, a);
        devolver(id, itemId);
        var nova = abrir(cliente2, a);
        assertThat(pagar(id, itemId).statusCode()).isEqualTo(200);
        problema(pagar(id, itemId), 409);
        var exemplar = json.readTree(enviar("GET", "/api/v1/exemplares/" + a, null).body());
        assertThat(exemplar.get("disponivel").asBoolean()).isFalse();
        assertThat(item(consultar(nova.get("id").asText()), a).get("dataPagamento").isNull()).isTrue();
    }

    @Test
    void exigeBasicECsrf() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        String id = locacao.get("id").asText(), body = corpo(itemId(locacao, a));
        devolver(id, itemId(locacao, a));
        var anonima = HttpRequest.newBuilder(uri(caminho(id))).header(csrfHeader, csrf)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertThat(client.send(anonima, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        var semToken = HttpRequest.newBuilder(uri(caminho(id))).header("Authorization", basic())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertThat(client.send(semToken, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(item(consultar(id), a).get("dataPagamento").isNull()).isTrue();
    }

    @Test
    void clockAnteriorADevolucaoNaoRegistraPagamento() throws Exception {
        var a = copia("A");
        var locacao = abrir(cliente, a);
        String id = locacao.get("id").asText();
        clock.data(INICIO.plusDays(1));
        devolver(id, itemId(locacao, a));
        clock.data(INICIO);
        problema(pagar(id, itemId(locacao, a)), 400);
        assertThat(item(consultar(id), a).get("dataPagamento").isNull()).isTrue();
    }

    private void concorrencia(String id, String bodyA, String bodyB) {
        var a = client.sendAsync(request("POST", caminho(id), bodyA), HttpResponse.BodyHandlers.ofString());
        var b = client.sendAsync(request("POST", caminho(id), bodyB), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a, b).orTimeout(30, TimeUnit.SECONDS).join();
        assertThat(new int[] {a.join().statusCode(), b.join().statusCode()}).containsExactlyInAnyOrder(200, 409);
    }

    private JsonNode abrir(UUID id, UUID... copias) throws Exception {
        var response = enviar("POST", "/api/v1/locacoes", abertura(id, copias));
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body());
    }

    private String abertura(UUID id, UUID... copias) {
        String itens = Arrays.stream(copias).map(copia -> "{\"exemplarId\":\"" + copia
                + "\",\"dataPrevistaDevolucao\":\"" + LocalDate.now(clock).plusDays(2) + "\"}")
                .collect(Collectors.joining(","));
        return "{\"clienteId\":\"" + id + "\",\"itens\":[" + itens + "]}";
    }

    private void devolver(String id, String... itens) throws Exception {
        assertThat(enviar("POST", "/api/v1/locacoes/" + id + "/devolucoes", corpo(itens)).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> pagar(String id, String... itens) throws Exception {
        return enviar("POST", caminho(id), corpo(itens));
    }

    private String corpo(String... ids) {
        return "{\"itens\":[" + Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
    }

    private JsonNode consultar(String id) throws Exception {
        var response = enviar("GET", "/api/v1/locacoes/" + id, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private JsonNode item(JsonNode locacao, UUID exemplar) {
        for (JsonNode item : locacao.get("itens")) {
            if (item.get("exemplarId").asText().equals(exemplar.toString())) { return item; }
        }
        throw new AssertionError("Item não encontrado");
    }

    private String itemId(JsonNode locacao, UUID exemplar) { return item(locacao, exemplar).get("id").asText(); }

    private String caminho(String id) { return "/api/v1/locacoes/" + id + "/pagamentos"; }

    private UUID inserirCliente(String cpf) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO locadora.clientes(id, nome, telefone, cpf) VALUES (?, ?, ?, ?)",
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
        if (body != null) { request.header("Content-Type", "application/json").header(csrfHeader, csrf); }
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    private String basic() {
        return "Basic " + Base64.getEncoder().encodeToString(
                "payment-test-operator:payment-test-only-password".getBytes(StandardCharsets.UTF_8));
    }
}
