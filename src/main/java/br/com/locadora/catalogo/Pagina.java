package br.com.locadora.catalogo;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public record Pagina<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    static <T> Pagina<T> de(Page<T> dados) {
        return new Pagina<>(dados.getContent(), dados.getNumber(), dados.getSize(),
                dados.getTotalElements(), dados.getTotalPages());
    }

    static PageRequest pedido(int page, int size, String ordenacao) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Paginação inválida.");
        }
        return PageRequest.of(page, size, Sort.by(ordenacao, "id"));
    }
}
