package br.com.locadora.catalogo;

import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExemplarService {
    private final ExemplarRepository exemplares;
    private final LivroRepository livros;
    private final HistoricoExemplares historico;

    public ExemplarService(ExemplarRepository exemplares, LivroRepository livros, HistoricoExemplares historico) {
        this.exemplares = exemplares;
        this.livros = livros;
        this.historico = historico;
    }

    @Transactional
    public ExemplarResponse criar(ExemplarRequest dados) {
        var livro = encontrarLivro(dados.livroId());
        if (exemplares.existsByCodigo(dados.codigo())) {
            throw new CodigoExemplarDuplicadoException();
        }
        return ExemplarResponse.de(exemplares.saveAndFlush(new Exemplar(livro, dados.codigo())), true);
    }

    public ExemplarResponse buscar(UUID id) {
        var exemplar = encontrar(id);
        return ExemplarResponse.de(exemplar, !historico.emprestados(List.of(id)).contains(id));
    }

    public Pagina<ExemplarResponse> listar(int page, int size) {
        var pagina = exemplares.findAll(Pagina.pedido(page, size, "codigo"));
        var emprestados = historico.emprestados(pagina.map(Exemplar::getId).getContent());
        return Pagina.de(pagina.map(exemplar -> ExemplarResponse.de(exemplar,
                !emprestados.contains(exemplar.getId()))));
    }

    @Transactional
    public ExemplarResponse atualizar(UUID id, ExemplarRequest dados) {
        var exemplar = exemplares.buscarComBloqueio(id).orElseThrow(ExemplarNaoEncontradoException::new);
        var livro = encontrarLivro(dados.livroId());
        if (!exemplar.getLivro().getId().equals(livro.getId()) && historico.temHistorico(id)) {
            throw new ExemplarComHistoricoException();
        }
        if (exemplares.existsByCodigoAndIdNot(dados.codigo(), id)) {
            throw new CodigoExemplarDuplicadoException();
        }
        exemplar.atualizar(livro, dados.codigo());
        return ExemplarResponse.de(exemplares.saveAndFlush(exemplar),
                !historico.emprestados(List.of(id)).contains(id));
    }

    private Exemplar encontrar(UUID id) {
        return exemplares.findById(id).orElseThrow(ExemplarNaoEncontradoException::new);
    }

    private Livro encontrarLivro(UUID id) {
        return livros.findById(id).orElseThrow(LivroNaoEncontradoException::new);
    }
}
