package br.com.locadora.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "exemplares", schema = "locadora")
public class Exemplar {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "livro_id", nullable = false)
    private Livro livro;

    @Column(nullable = false, length = 50)
    private String codigo;

    protected Exemplar() {}

    Exemplar(Livro livro, String codigo) {
        atualizar(livro, codigo);
    }

    void atualizar(Livro livro, String codigo) {
        this.livro = livro;
        this.codigo = codigo;
    }

    public UUID getId() { return id; }
    public Livro getLivro() { return livro; }
    public String getCodigo() { return codigo; }
}
