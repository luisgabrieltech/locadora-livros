package br.com.locadora.catalogo;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

// Contrato do catálogo; o módulo de locações fornece a consulta do histórico.
public interface HistoricoExemplares {
    Set<UUID> emprestados(Collection<UUID> ids);
    boolean temHistorico(UUID exemplarId);
}
