package br.com.locadora.locacoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record LocacaoResponse(UUID id, UUID clienteId, LocalDate dataRetirada, List<Item> itens, boolean encerrada) {
    static LocacaoResponse de(Locacao locacao) {
        return new LocacaoResponse(locacao.getId(), locacao.getCliente().getId(), locacao.getDataRetirada(),
                locacao.getItens().stream().sorted(Comparator.comparing(i -> i.getExemplar().getId()))
                        .map(i -> new Item(i.getId(), i.getExemplar().getId(), i.getDataPrevistaDevolucao(),
                                i.getValorDiaria(), i.getValorMultaDiaria(), i.getDataDevolucao(),
                                i.getValorTotal(), i.getDataPagamento())).toList(), locacao.encerrada());
    }

    public record Item(UUID id, UUID exemplarId, LocalDate dataPrevistaDevolucao,
                       BigDecimal valorDiaria, BigDecimal valorMultaDiaria, LocalDate dataDevolucao,
                       BigDecimal valorTotal, LocalDate dataPagamento) {}

    public record Resumo(UUID id, UUID clienteId, LocalDate dataRetirada,
                         boolean encerrada, boolean atrasada, boolean pagamentoPendente) {
        static Resumo de(LocacaoRepository.ResumoConsulta locacao) {
            return new Resumo(locacao.getId(), locacao.getClienteId(), locacao.getDataRetirada(),
                    locacao.getEncerrada(), locacao.getAtrasada(), locacao.getPagamentoPendente());
        }
    }

    public record Pagina(List<Resumo> items, int page, int size, long totalElements, int totalPages) {
        static Pagina de(Page<LocacaoRepository.ResumoConsulta> pagina) {
            return new Pagina(pagina.map(Resumo::de).getContent(), pagina.getNumber(), pagina.getSize(),
                    pagina.getTotalElements(), pagina.getTotalPages());
        }
    }
}
