package br.com.locadora.locacoes;

import br.com.locadora.catalogo.HistoricoExemplares;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ConsultaHistoricoExemplares implements HistoricoExemplares {
    private final ItemLocacaoRepository itens;

    public ConsultaHistoricoExemplares(ItemLocacaoRepository itens) {
        this.itens = itens;
    }

    @Override
    public Set<UUID> emprestados(Collection<UUID> ids) {
        return ids.isEmpty() ? Set.of() : itens.emprestados(ids);
    }

    @Override
    public boolean temHistorico(UUID exemplarId) {
        return itens.existsByExemplarId(exemplarId);
    }
}
