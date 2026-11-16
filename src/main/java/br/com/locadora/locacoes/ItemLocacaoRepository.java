package br.com.locadora.locacoes;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemLocacaoRepository extends JpaRepository<ItemLocacao, UUID> {
    @Query("""
            select i.exemplar.id from ItemLocacao i
            where i.dataDevolucao is null and i.exemplar.id in :ids
            """)
    Set<UUID> emprestados(@Param("ids") Collection<UUID> ids);

    boolean existsByExemplarId(UUID exemplarId);

    @Query("""
            select (count(i) > 0) from ItemLocacao i
            where i.locacao.cliente.id = :clienteId and (
                (i.dataDevolucao is null and i.dataPrevistaDevolucao < :hoje)
                or (i.dataDevolucao is not null and i.dataPagamento is null and i.valorTotal > 0)
            )
            """)
    boolean clienteBloqueado(@Param("clienteId") UUID clienteId, @Param("hoje") LocalDate hoje);
}
