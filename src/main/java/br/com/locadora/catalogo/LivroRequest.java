package br.com.locadora.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.ISBN;

public record LivroRequest(
        @NotBlank(message = "Informe o título.")
        @CodePointLength(max = 200, message = "O título deve ter no máximo 200 caracteres.") String titulo,
        @NotBlank(message = "Informe o autor.")
        @CodePointLength(max = 150, message = "O autor deve ter no máximo 150 caracteres.") String autor,
        @Pattern(regexp = "([0-9]{9}[0-9X]|97[89][0-9]{10})", message = "Informe ISBN-10 ou ISBN-13.")
        @ISBN(type = ISBN.Type.ANY, message = "Informe um ISBN válido.") String isbn) {

    public LivroRequest {
        titulo = titulo == null ? null : titulo.strip();
        autor = autor == null ? null : autor.strip();
        if (isbn == null || isbn.isBlank()) {
            isbn = null;
        } else {
            // Remover somente separadores convencionais, sem ocultar letras inválidas.
            isbn = isbn.strip().replace("-", "").replace(" ", "").replace('x', 'X');
        }
    }
}
