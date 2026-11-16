package br.com.locadora.catalogo;

import java.util.UUID;

public record ExemplarResponse(UUID id, UUID livroId, String codigo, boolean disponivel) {
    static ExemplarResponse de(Exemplar exemplar, boolean disponivel) {
        return new ExemplarResponse(exemplar.getId(), exemplar.getLivro().getId(), exemplar.getCodigo(), disponivel);
    }
}
