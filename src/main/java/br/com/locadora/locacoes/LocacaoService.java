package br.com.locadora.locacoes;

import br.com.locadora.catalogo.Exemplar;
import br.com.locadora.catalogo.ExemplarNaoEncontradoException;
import br.com.locadora.catalogo.ExemplarRepository;
import br.com.locadora.clientes.ClienteNaoEncontradoException;
import br.com.locadora.clientes.ClienteRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LocacaoService {
    private final LocacaoRepository locacoes;
    private final ItemLocacaoRepository itens;
    private final ClienteRepository clientes;
    private final ExemplarRepository exemplares;
    private final TarifaProperties tarifa;
    private final Clock clock;

    public LocacaoService(LocacaoRepository locacoes, ItemLocacaoRepository itens,
            ClienteRepository clientes, ExemplarRepository exemplares, TarifaProperties tarifa, Clock clock) {
        this.locacoes = locacoes;
        this.itens = itens;
        this.clientes = clientes;
        this.exemplares = exemplares;
        this.tarifa = tarifa;
        this.clock = clock;
    }

    @Transactional
    public LocacaoResponse abrir(LocacaoRequest dados) {
        RegrasAbertura.validar(dados, LocalDate.now(clock));
        var cliente = clientes.buscarComBloqueio(dados.clienteId()).orElseThrow(ClienteNaoEncontradoException::new);

        // Ordem global de bloqueios: cliente primeiro, exemplares em ordem de UUID.
        // Nenhuma gravação ocorre antes de verificar todos os itens.
        var ids = dados.itens().stream().map(LocacaoRequest.Item::exemplarId).sorted().toList();
        var copias = new HashMap<UUID, Exemplar>();
        for (var id : ids) {
            copias.put(id, exemplares.buscarComBloqueio(id).orElseThrow(ExemplarNaoEncontradoException::new));
        }
        // A espera por qualquer bloqueio pode atravessar a meia-noite.
        var hoje = LocalDate.now(clock);
        RegrasAbertura.validar(dados, hoje);
        if (itens.clienteBloqueado(cliente.getId(), hoje)) {
            throw new LocacaoConflitoException("Cliente com atraso ou pagamento pendente.");
        }
        if (!itens.emprestados(ids).isEmpty()) {
            throw new LocacaoConflitoException("Um ou mais exemplares já estão emprestados.");
        }
        var locacao = new Locacao(cliente, hoje);
        dados.itens().forEach(item ->
                locacao.adicionar(copias.get(item.exemplarId()), item.dataPrevistaDevolucao(), tarifa));
        return LocacaoResponse.de(locacoes.saveAndFlush(locacao));
    }

    public LocacaoResponse buscar(UUID id) {
        return LocacaoResponse.de(locacoes.buscarDetalhes(id).orElseThrow(LocacaoNaoEncontradaException::new));
    }

    public LocacaoResponse.Pagina listar(int page, int size, UUID clienteId, SituacaoLocacao situacao) {
        if (page < 0 || size < 1 || size > 100) {
            throw new LocacaoInvalidaException("Página não negativa e tamanho entre 1 e 100 são obrigatórios.");
        }
        return LocacaoResponse.Pagina.de(
                locacoes.consultar(clienteId, situacao == null ? null : situacao.name(),
                        LocalDate.now(clock), PageRequest.of(page, size)));
    }
}
