package br.com.locadora.locacoes;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.locacao")
public record TarifaProperties(
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal diaria,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal multaDiaria) {}
