package br.com.locadora.locacoes;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocacaoRepository extends JpaRepository<Locacao, UUID> {
    String RESUMOS = """
            select l.id, l.cliente_id, l.data_retirada,
                (count(i.id) > 0 and bool_and(i.data_devolucao is not null
                    and i.data_pagamento is not null)) as encerrada,
                bool_or(i.data_devolucao is null and i.data_prevista_devolucao < :hoje) as atrasada,
                bool_or(i.data_devolucao is not null and i.data_pagamento is null
                    and i.valor_total > 0) as pagamento_pendente
            from locadora.locacoes l
            join locadora.itens_locacao i on i.locacao_id = l.id
            where (cast(:clienteId as uuid) is null or l.cliente_id = cast(:clienteId as uuid))
            group by l.id
            """;
    String FILTRO_SITUACAO = """
            where (cast(:situacao as text) is null
                or (:situacao = 'ABERTA' and not r.encerrada)
                or (:situacao = 'ATRASADA' and r.atrasada)
                or (:situacao = 'PAGAMENTO_PENDENTE' and r.pagamento_pendente)
                or (:situacao = 'ENCERRADA' and r.encerrada))
            """;

    // Uma linha por locação; paginação/contagem no banco sem carregar coleções de itens.
    @Query(value = """
            select r.id, r.cliente_id as "clienteId", r.data_retirada as "dataRetirada",
                r.encerrada, r.atrasada, r.pagamento_pendente as "pagamentoPendente"
            from (
            """ + RESUMOS + ") r " + FILTRO_SITUACAO + " order by r.data_retirada, r.id",
            countQuery = "select count(*) from (" + RESUMOS + ") r " + FILTRO_SITUACAO,
            nativeQuery = true)
    Page<ResumoConsulta> consultar(@Param("clienteId") UUID clienteId,
            @Param("situacao") String situacao, @Param("hoje") LocalDate hoje, Pageable pagina);

    interface ResumoConsulta {
        UUID getId();
        UUID getClienteId();
        LocalDate getDataRetirada();
        boolean getEncerrada();
        boolean getAtrasada();
        boolean getPagamentoPendente();
    }

    @Query("select l.cliente.id from Locacao l where l.id = :id")
    Optional<UUID> buscarClienteId(@Param("id") UUID id);

    @EntityGraph(attributePaths = "itens")
    @Query("select l from Locacao l where l.id = :id")
    Optional<Locacao> buscarDetalhes(@Param("id") UUID id);
}
