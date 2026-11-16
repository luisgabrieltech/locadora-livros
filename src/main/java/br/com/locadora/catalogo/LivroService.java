package br.com.locadora.catalogo;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LivroService {
    private final LivroRepository repository;

    public LivroService(LivroRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LivroResponse criar(LivroRequest dados) {
        return LivroResponse.de(repository.saveAndFlush(new Livro(dados)));
    }

    public LivroResponse buscar(UUID id) {
        return LivroResponse.de(encontrar(id));
    }

    public Pagina<LivroResponse> listar(int page, int size) {
        return Pagina.de(repository.findAll(Pagina.pedido(page, size, "titulo")).map(LivroResponse::de));
    }

    @Transactional
    public LivroResponse atualizar(UUID id, LivroRequest dados) {
        var livro = encontrar(id);
        livro.atualizar(dados);
        return LivroResponse.de(repository.saveAndFlush(livro));
    }

    private Livro encontrar(UUID id) {
        return repository.findById(id).orElseThrow(LivroNaoEncontradoException::new);
    }
}
