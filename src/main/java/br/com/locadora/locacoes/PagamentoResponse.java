package br.com.locadora.locacoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PagamentoResponse(UUID locacaoId, LocalDate dataPagamento, List<Item> itens,
                                BigDecimal valorTotalPago, boolean locacaoEncerrada) {
    public record Item(UUID itemId, UUID exemplarId, BigDecimal valorPago) {}
}
