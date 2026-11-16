package br.com.locadora.locacoes;

import br.com.locadora.catalogo.ExemplarNaoEncontradoException;
import br.com.locadora.catalogo.ExemplarRepository;
import br.com.locadora.clientes.ClienteNaoEncontradoException;
import br.com.locadora.clientes.ClienteRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevolucaoService {
    private final LocacaoRepository locacoes;
    private final ItemLocacaoRepository itens;
    private final ClienteRepository clientes;
    private final ExemplarRepository exemplares;
    private final Clock clock;

    public DevolucaoService(LocacaoRepository locacoes, ItemLocacaoRepository itens,
            ClienteRepository clientes, ExemplarRepository exemplares, Clock clock) {
        this.locacoes = locacoes;
        this.itens = itens;
        this.clientes = clientes;
        this.exemplares = exemplares;
        this.clock = clock;
    }

    @Transactional
    public DevolucaoResponse devolver(UUID locacaoId, DevolucaoRequest dados) {
        validarLista(dados);
        // A projeção não carrega itens no contexto antes de adquirir o lock.
        // Assim, quem aguarda outra devolução lê o estado atualizado após o lock.
        var clienteId = locacoes.buscarClienteId(locacaoId).orElseThrow(LocacaoNaoEncontradaException::new);
        clientes.buscarComBloqueio(clienteId).orElseThrow(ClienteNaoEncontradoException::new);
        // Não consultar elegibilidade: mesmo um cliente bloqueado pode devolver.
        var locacao = locacoes.buscarDetalhes(locacaoId).orElseThrow(LocacaoNaoEncontradaException::new);
        var porId = locacao.getItens().stream().collect(java.util.stream.Collectors.toMap(ItemLocacao::getId, i -> i));
        var selecionados = new ArrayList<ItemLocacao>();
        for (var id : dados.itens()) {
            var item = porId.get(id);
            if (item == null) {
                throw new ItemLocacaoNaoEncontradoException();
            }
            if (item.getDataDevolucao() != null) {
                throw new LocacaoConflitoException("Um ou mais itens já foram devolvidos.");
            }
            selecionados.add(item);
        }
        selecionados.sort(Comparator.comparing(i -> i.getExemplar().getId()));
        for (var item : selecionados) {
            exemplares.buscarComBloqueio(item.getExemplar().getId()).orElseThrow(ExemplarNaoEncontradoException::new);
        }
        var hoje = LocalDate.now(clock);
        // Calcular o lote inteiro antes de alterar qualquer item.
        var calculos = selecionados.stream().map(item -> CalculoDevolucao.calcular(
                locacao.getDataRetirada(), item.getDataPrevistaDevolucao(), hoje,
                item.getValorDiaria(), item.getValorMultaDiaria())).toList();
        List<DevolucaoResponse.Item> resultados = new ArrayList<>();
        for (int i = 0; i < selecionados.size(); i++) {
            var item = selecionados.get(i);
            var valores = calculos.get(i);
            item.devolver(hoje, valores.valorTotal());
            resultados.add(DevolucaoResponse.Item.de(item, valores));
        }
        itens.flush();
        return new DevolucaoResponse(locacaoId, hoje, List.copyOf(resultados));
    }

    private void validarLista(DevolucaoRequest dados) {
        if (dados == null || dados.itens() == null || dados.itens().isEmpty()
                || dados.itens().stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(dados.itens()).size() != dados.itens().size()) {
            throw new LocacaoInvalidaException("Informe itens não repetidos para a devolução.");
        }
    }
}
