package br.com.locadora.locacoes;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;

final class RegrasAbertura {
    // Limite do tipo DATE do PostgreSQL, sem impor prazo máximo de negócio.
    private static final LocalDate PRAZO_MAXIMO_PERSISTIVEL = LocalDate.of(5874897, 12, 31);
    private RegrasAbertura() {}

    static void validar(LocacaoRequest dados, LocalDate retirada) {
        if (dados.clienteId() == null || dados.itens() == null || dados.itens().isEmpty()) {
            throw new LocacaoInvalidaException("Informe cliente e ao menos um exemplar.");
        }
        var ids = new HashSet<UUID>();
        for (var item : dados.itens()) {
            if (item == null || item.exemplarId() == null || item.dataPrevistaDevolucao() == null) {
                throw new LocacaoInvalidaException("Informe exemplar e prazo em todos os itens.");
            }
            if (!ids.add(item.exemplarId())) {
                throw new LocacaoInvalidaException("Não repita exemplares na mesma locação.");
            }
            if (item.dataPrevistaDevolucao().isBefore(retirada)) {
                throw new LocacaoInvalidaException("O prazo não pode ser anterior à retirada.");
            }
            if (item.dataPrevistaDevolucao().isAfter(PRAZO_MAXIMO_PERSISTIVEL)) {
                throw new LocacaoInvalidaException("O prazo excede a faixa de datas suportada.");
            }
        }
    }
}
