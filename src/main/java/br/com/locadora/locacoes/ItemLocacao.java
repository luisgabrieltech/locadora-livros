package br.com.locadora.locacoes;

import br.com.locadora.catalogo.Exemplar;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "itens_locacao", schema = "locadora")
public class ItemLocacao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locacao_id", nullable = false)
    private Locacao locacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exemplar_id", nullable = false)
    private Exemplar exemplar;

    @Column(nullable = false)
    private LocalDate dataPrevistaDevolucao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valorDiaria;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valorMultaDiaria;

    private LocalDate dataDevolucao;
    @Column(precision = 12, scale = 2)
    private BigDecimal valorTotal;
    private LocalDate dataPagamento;

    protected ItemLocacao() {}

    ItemLocacao(Locacao locacao, Exemplar exemplar, LocalDate prazo, TarifaProperties tarifa) {
        this.locacao = locacao;
        this.exemplar = exemplar;
        this.dataPrevistaDevolucao = prazo;
        this.valorDiaria = tarifa.diaria();
        this.valorMultaDiaria = tarifa.multaDiaria();
    }

    public UUID getId() { return id; }
    public Exemplar getExemplar() { return exemplar; }
    public LocalDate getDataPrevistaDevolucao() { return dataPrevistaDevolucao; }
    public BigDecimal getValorDiaria() { return valorDiaria; }
    public BigDecimal getValorMultaDiaria() { return valorMultaDiaria; }
    public LocalDate getDataDevolucao() { return dataDevolucao; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public LocalDate getDataPagamento() { return dataPagamento; }

    void devolver(LocalDate data, BigDecimal total) {
        if (dataDevolucao != null) {
            throw new LocacaoConflitoException("Item já devolvido.");
        }
        dataDevolucao = data;
        valorTotal = total;
        // Não preencher dataPagamento: devolução e quitação são operações distintas.
    }

    void quitar(LocalDate data) {
        if (dataDevolucao == null || valorTotal == null) {
            throw new LocacaoConflitoException("Só é permitido pagar itens já devolvidos.");
        }
        if (dataPagamento != null) {
            throw new LocacaoConflitoException("Item já pago.");
        }
        if (data == null || data.isBefore(dataDevolucao)) {
            throw new LocacaoInvalidaException("Data de pagamento inválida.");
        }
        dataPagamento = data;
    }
}
