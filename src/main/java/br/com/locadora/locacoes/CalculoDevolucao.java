package br.com.locadora.locacoes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class CalculoDevolucao {
    private static final BigDecimal LIMITE_MONETARIO = new BigDecimal("9999999999.99");

    private CalculoDevolucao() {}

    static Valores calcular(LocalDate retirada, LocalDate prevista, LocalDate devolucao,
            BigDecimal diaria, BigDecimal multaDiaria) {
        if (retirada == null || prevista == null || devolucao == null
                || devolucao.isBefore(retirada) || prevista.isBefore(retirada)) {
            throw new LocacaoInvalidaException("Datas inconsistentes para calcular a devolução.");
        }
        if (diaria == null || multaDiaria == null || diaria.signum() <= 0 || multaDiaria.signum() < 0) {
            throw new LocacaoInvalidaException("Tarifas inconsistentes para calcular a devolução.");
        }
        long dias = Math.max(1, ChronoUnit.DAYS.between(retirada, devolucao));
        long atraso = Math.max(0, ChronoUnit.DAYS.between(prevista, devolucao));
        try {
            var diarias = diaria.multiply(BigDecimal.valueOf(dias)).setScale(2, RoundingMode.UNNECESSARY);
            var multa = multaDiaria.multiply(BigDecimal.valueOf(atraso)).setScale(2, RoundingMode.UNNECESSARY);
            var total = diarias.add(multa);
            if (total.compareTo(LIMITE_MONETARIO) > 0) {
                throw new LocacaoInvalidaException("O valor calculado excede o limite monetário da aplicação.");
            }
            return new Valores(dias, atraso, diarias, multa, total);
        } catch (ArithmeticException exception) {
            throw new LocacaoInvalidaException("Os valores não podem exigir arredondamento de centavos.");
        }
    }

    record Valores(long diasCobrados, long diasAtraso, BigDecimal valorDiarias,
                   BigDecimal valorMulta, BigDecimal valorTotal) {}
}
