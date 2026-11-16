package br.com.locadora.catalogo;

import java.util.UUID;

public record LivroResponse(UUID id, String titulo, String autor, String isbn) {
    static LivroResponse de(Livro livro) {
        return new LivroResponse(livro.getId(), livro.getTitulo(), livro.getAutor(), livro.getIsbn());
    }
}
