package br.com.locadora.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "livros", schema = "locadora")
public class Livro {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, length = 150)
    private String autor;

    @Column(length = 13)
    private String isbn;

    protected Livro() {}

    Livro(LivroRequest dados) {
        atualizar(dados);
    }

    void atualizar(LivroRequest dados) {
        titulo = dados.titulo();
        autor = dados.autor();
        isbn = dados.isbn();
    }

    public UUID getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getAutor() { return autor; }
    public String getIsbn() { return isbn; }
}
