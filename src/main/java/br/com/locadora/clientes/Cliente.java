package br.com.locadora.clientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "clientes", schema = "locadora")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 15)
    private String telefone;

    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(length = 254)
    private String email;

    protected Cliente() {}

    Cliente(ClienteRequest dados) {
        atualizar(dados);
    }

    void atualizar(ClienteRequest dados) {
        nome = dados.nome();
        telefone = dados.telefone();
        cpf = dados.cpf();
        email = dados.email();
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getTelefone() { return telefone; }
    public String getCpf() { return cpf; }
    public String getEmail() { return email; }
}
