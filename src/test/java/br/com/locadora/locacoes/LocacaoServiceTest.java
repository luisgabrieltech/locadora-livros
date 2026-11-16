package br.com.locadora.locacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.locadora.catalogo.Exemplar;
import br.com.locadora.catalogo.ExemplarRepository;
import br.com.locadora.clientes.Cliente;
import br.com.locadora.clientes.ClienteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocacaoServiceTest {
    private final LocacaoRepository locacoes = mock(LocacaoRepository.class);
    private final ItemLocacaoRepository itens = mock(ItemLocacaoRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final ExemplarRepository exemplares = mock(ExemplarRepository.class);
    private final Clock clock = mock(Clock.class);
    private final UUID clienteId = UUID.randomUUID();
    private final UUID exemplarId = UUID.randomUUID();
    private final LocalDate amanha = LocalDate.of(2026, 11, 17);
    private final AtomicReference<Instant> instante =
            new AtomicReference<>(Instant.parse("2026-11-17T02:59:59Z"));

    private LocacaoService preparar(boolean mudaNoCliente) {
        when(clock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        when(clock.instant()).thenAnswer(invocacao -> instante.get());
        var cliente = mock(Cliente.class);
        when(cliente.getId()).thenReturn(clienteId);
        when(clientes.buscarComBloqueio(clienteId)).thenAnswer(invocacao -> {
            if (mudaNoCliente) virarDia();
            return Optional.of(cliente);
        });
        var exemplar = mock(Exemplar.class);
        when(exemplar.getId()).thenReturn(exemplarId);
        when(exemplares.buscarComBloqueio(exemplarId)).thenAnswer(invocacao -> {
            if (!mudaNoCliente) virarDia();
            return Optional.of(exemplar);
        });
        when(locacoes.saveAndFlush(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        return new LocacaoService(locacoes, itens, clientes, exemplares,
                new TarifaProperties(new BigDecimal("2.00"), new BigDecimal("1.00")), clock);
    }

    @Test
    void bloqueiaClienteQueFicouAtrasadoDuranteLockDoCliente() {
        var service = preparar(true);
        when(itens.clienteBloqueado(clienteId, amanha)).thenReturn(true);
        assertThatThrownBy(() -> service.abrir(dados(amanha)))
                .isInstanceOf(LocacaoConflitoException.class);
        verify(itens).clienteBloqueado(clienteId, amanha);
        verifyNoInteractions(locacoes);
    }

    @Test
    void revalidaPrazoDepoisDeEsperarLockDoExemplar() {
        var service = preparar(false);
        assertThatThrownBy(() -> service.abrir(dados(amanha.minusDays(1))))
                .isInstanceOf(LocacaoInvalidaException.class);
        verifyNoInteractions(locacoes);
    }

    @Test
    void usaMesmaDataAtualNaElegibilidadeENaRetiradaAposLockDoExemplar() {
        var service = preparar(false);
        assertThat(service.abrir(dados(amanha)).dataRetirada()).isEqualTo(amanha);
        verify(itens).clienteBloqueado(clienteId, amanha);
    }

    private LocacaoRequest dados(LocalDate prazo) {
        return new LocacaoRequest(clienteId, List.of(new LocacaoRequest.Item(exemplarId, prazo)));
    }

    private void virarDia() {
        instante.set(Instant.parse("2026-11-17T03:00:01Z"));
    }
}
