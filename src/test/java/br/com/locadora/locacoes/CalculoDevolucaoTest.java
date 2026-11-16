package br.com.locadora.locacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculoDevolucaoTest {

    @ParameterizedTest
    @CsvSource({
        "2026-11-10,2026-11-12,2026-11-10,1,0,2.00",
        "2026-11-10,2026-11-12,2026-11-11,1,0,2.00",
        "2026-11-10,2026-11-12,2026-11-12,2,0,4.00",
        "2026-11-10,2026-11-12,2026-11-14,4,2,10.00",
        "2026-11-30,2026-12-01,2026-12-02,2,1,5.00",
        "2024-02-28,2024-02-29,2024-03-01,2,1,5.00"
    })
    void calculaMinimoAntecipacaoPrazoEAtraso(String retirada, String prevista, String devolucao,
            long dias, long atraso, String total) {
        var resultado = CalculoDevolucao.calcular(LocalDate.parse(retirada), LocalDate.parse(prevista),
                LocalDate.parse(devolucao), new BigDecimal("2.00"), new BigDecimal("1.00"));
        assertThat(resultado.diasCobrados()).isEqualTo(dias);
        assertThat(resultado.diasAtraso()).isEqualTo(atraso);
        assertThat(resultado.valorTotal()).isEqualByComparingTo(total);
        assertThat(resultado.valorDiarias().add(resultado.valorMulta())).isEqualByComparingTo(total);
    }

    @Test
    void centavosSaoExatos() {
        var resultado = CalculoDevolucao.calcular(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 13), new BigDecimal("0.10"), new BigDecimal("0.03"));
        assertThat(resultado.valorDiarias()).isEqualByComparingTo("0.30");
        assertThat(resultado.valorMulta()).isEqualByComparingTo("0.06");
        assertThat(resultado.valorTotal()).isEqualByComparingTo("0.36");
    }

    @Test
    void rejeitaDatasInconsistentesTarifasInvalidasEArredondamento() {
        var dia = LocalDate.of(2026, 11, 10);
        assertThatThrownBy(() -> CalculoDevolucao.calcular(dia, dia, dia.minusDays(1),
                BigDecimal.ONE, BigDecimal.ZERO)).isInstanceOf(LocacaoInvalidaException.class);
        assertThatThrownBy(() -> CalculoDevolucao.calcular(dia, dia.minusDays(1), dia,
                BigDecimal.ONE, BigDecimal.ZERO)).isInstanceOf(LocacaoInvalidaException.class);
        assertThatThrownBy(() -> CalculoDevolucao.calcular(dia, dia, dia,
                BigDecimal.ZERO, BigDecimal.ONE)).isInstanceOf(LocacaoInvalidaException.class);
        assertThatThrownBy(() -> CalculoDevolucao.calcular(dia, dia, dia,
                new BigDecimal("1.001"), BigDecimal.ZERO)).isInstanceOf(LocacaoInvalidaException.class);
    }

    @Test
    void rejeitaValorAcimaDaCapacidadeDePersistencia() {
        var dia = LocalDate.of(2026, 11, 10);
        assertThatThrownBy(() -> CalculoDevolucao.calcular(dia, dia.plusDays(3), dia.plusDays(2),
                new BigDecimal("9999999999.99"), BigDecimal.ZERO))
                .isInstanceOf(LocacaoInvalidaException.class);
    }
}
