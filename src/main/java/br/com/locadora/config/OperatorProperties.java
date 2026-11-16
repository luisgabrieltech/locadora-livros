package br.com.locadora.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.operator")
public record OperatorProperties(@NotBlank String username, @NotBlank String password) {

    @Override
    public String toString() {
        return "OperatorProperties[credentials=REDACTED]";
    }
}
