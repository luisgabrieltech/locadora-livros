package br.com.locadora.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.hibernate.validator.constraints.CodePointLength;

public record ExemplarRequest(
        @NotNull(message = "Informe o livro.") UUID livroId,
        @NotBlank(message = "Informe o código do exemplar.")
        @CodePointLength(max = 50, message = "O código deve ter no máximo 50 caracteres.") String codigo) {

    public ExemplarRequest {
        codigo = codigo == null ? null : codigo.strip();
    }
}
