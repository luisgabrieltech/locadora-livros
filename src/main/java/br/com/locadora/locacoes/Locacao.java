package br.com.locadora.locacoes;

import br.com.locadora.catalogo.Exemplar;
import br.com.locadora.clientes.Cliente;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.Column;

@Entity
@Table(name = "locacoes", schema = "locadora")
public class Locacao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private LocalDate dataRetirada;

    @OneToMany(mappedBy = "locacao", cascade = CascadeType.PERSIST)
    private List<ItemLocacao> itens = new ArrayList<>();

    protected Locacao() {}

    Locacao(Cliente cliente, LocalDate dataRetirada) {
        this.cliente = cliente;
        this.dataRetirada = dataRetirada;
    }

    void adicionar(Exemplar exemplar, LocalDate prazo, TarifaProperties tarifa) {
        itens.add(new ItemLocacao(this, exemplar, prazo, tarifa));
    }

    public UUID getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public LocalDate getDataRetirada() { return dataRetirada; }
    public List<ItemLocacao> getItens() { return List.copyOf(itens); }

    public boolean encerrada() {
        return !itens.isEmpty() && itens.stream().allMatch(item ->
                item.getDataDevolucao() != null && item.getDataPagamento() != null);
    }
}
