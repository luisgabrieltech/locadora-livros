package br.com.locadora.locacoes;

import br.com.locadora.catalogo.ExemplarNaoEncontradoException;
import br.com.locadora.catalogo.ExemplarRepository;
import br.com.locadora.clientes.ClienteNaoEncontradoException;
import br.com.locadora.clientes.ClienteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PagamentoService {
    private final LocacaoRepository locacoes;
    private final ItemLocacaoRepository itens;
    private final ClienteRepository clientes;
    private final ExemplarRepository exemplares;
    private final Clock clock;

    public PagamentoService(LocacaoRepository locacoes, ItemLocacaoRepository itens,
            ClienteRepository clientes, ExemplarRepository exemplares, Clock clock) {
        this.locacoes = locacoes;
        this.itens = itens;
        this.clientes = clientes;
        this.exemplares = exemplares;
        this.clock = clock;
    }

    @Transactional
    public PagamentoResponse quitar(UUID locacaoId, PagamentoRequest dados) {
        if (dados == null || dados.itens() == null || dados.itens().isEmpty()
                || dados.itens().stream().anyMatch(Objects::isNull)
                || new HashSet<>(dados.itens()).size() != dados.itens().size()) {
            throw new LocacaoInvalidaException("Informe itens não repetidos para o pagamento.");
        }
        var clienteId = locacoes.buscarClienteId(locacaoId).orElseThrow(LocacaoNaoEncontradaException::new);
        clientes.buscarComBloqueio(clienteId).orElseThrow(ClienteNaoEncontradoException::new);
        // Carregar os itens somente após o lock para observar quitações concorrentes.
        var locacao = locacoes.buscarDetalhes(locacaoId).orElseThrow(LocacaoNaoEncontradaException::new);
        var porId = locacao.getItens().stream().collect(Collectors.toMap(ItemLocacao::getId, i -> i));
        var selecionados = new ArrayList<ItemLocacao>();
        for (var id : dados.itens()) {
            var item = porId.get(id);
            if (item == null) {
                throw new ItemLocacaoNaoEncontradoException();
            }
            if (item.getDataDevolucao() == null || item.getValorTotal() == null) {
                throw new LocacaoConflitoException("Só é permitido pagar itens já devolvidos.");
            }
            if (item.getDataPagamento() != null) {
                throw new LocacaoConflitoException("Um ou mais itens já foram pagos.");
            }
            selecionados.add(item);
        }
        selecionados.sort(Comparator.comparing(i -> i.getExemplar().getId()));
        for (var item : selecionados) {
            exemplares.buscarComBloqueio(item.getExemplar().getId()).orElseThrow(ExemplarNaoEncontradoException::new);
        }
        var hoje = LocalDate.now(clock);
        if (selecionados.stream().anyMatch(i -> hoje.isBefore(i.getDataDevolucao()))) {
            throw new LocacaoInvalidaException("A data de pagamento não pode ser anterior à devolução.");
        }

        List<PagamentoResponse.Item> resultados = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (var item : selecionados) {
            item.quitar(hoje);
            total = total.add(item.getValorTotal());
            resultados.add(new PagamentoResponse.Item(item.getId(), item.getExemplar().getId(), item.getValorTotal()));
        }
        itens.flush();
        return new PagamentoResponse(locacaoId, hoje, List.copyOf(resultados), total, locacao.encerrada());
    }
}
