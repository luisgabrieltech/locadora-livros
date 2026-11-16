package br.com.locadora.locacoes;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegrasAberturaTest {
    private final LocalDate hoje = LocalDate.of(2026, 11, 16);

    @Test
    void aceitaMesmoDiaEPrazosDistintos() {
        var dados = new LocacaoRequest(UUID.randomUUID(), List.of(
                new LocacaoRequest.Item(UUID.randomUUID(), hoje),
                new LocacaoRequest.Item(UUID.randomUUID(), hoje.plusDays(3))));
        assertThatCode(() -> RegrasAbertura.validar(dados, hoje)).doesNotThrowAnyException();
    }

    @Test
    void rejeitaExemplarRepetidoEDataPassada() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> RegrasAbertura.validar(new LocacaoRequest(UUID.randomUUID(), List.of(
                new LocacaoRequest.Item(id, hoje), new LocacaoRequest.Item(id, hoje.plusDays(1)))), hoje))
                .isInstanceOf(LocacaoInvalidaException.class);
        assertThatThrownBy(() -> RegrasAbertura.validar(new LocacaoRequest(UUID.randomUUID(),
                List.of(new LocacaoRequest.Item(id, hoje.minusDays(1)))), hoje))
                .isInstanceOf(LocacaoInvalidaException.class);
    }

    @Test
    void rejeitaListasEItensIncompletos() {
        for (var dados : List.of(
                new LocacaoRequest(null, List.of()),
                new LocacaoRequest(UUID.randomUUID(), null),
                new LocacaoRequest(UUID.randomUUID(), List.of()),
                new LocacaoRequest(UUID.randomUUID(), Arrays.asList((LocacaoRequest.Item) null)),
                new LocacaoRequest(UUID.randomUUID(), List.of(new LocacaoRequest.Item(null, hoje))),
                new LocacaoRequest(UUID.randomUUID(), List.of(new LocacaoRequest.Item(UUID.randomUUID(), null))))) {
            assertThatThrownBy(() -> RegrasAbertura.validar(dados, hoje))
                    .isInstanceOf(LocacaoInvalidaException.class);
        }
    }
}
