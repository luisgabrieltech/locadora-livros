package br.com.locadora.locacoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DevolucaoResponse(UUID locacaoId, LocalDate dataDevolucao, List<Item> itens) {
    public record Item(UUID itemId, UUID exemplarId, long diasCobrados, long diasAtraso,
                       BigDecimal valorDiarias, BigDecimal valorMulta, BigDecimal valorTotal) {
        static Item de(ItemLocacao item, CalculoDevolucao.Valores valores) {
            return new Item(item.getId(), item.getExemplar().getId(), valores.diasCobrados(),
                    valores.diasAtraso(), valores.valorDiarias(), valores.valorMulta(), valores.valorTotal());
        }
    }
}
