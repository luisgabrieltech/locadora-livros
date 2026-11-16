package br.com.locadora.clientes;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record ClienteResponse(UUID id, String nome, String telefone, String cpf, String email) {

    static ClienteResponse de(Cliente cliente) {
        return new ClienteResponse(cliente.getId(), cliente.getNome(), cliente.getTelefone(),
                cliente.getCpf(), cliente.getEmail());
    }

    @Override
    public String toString() {
        return "ClienteResponse[id=" + id + ", dados pessoais omitidos]";
    }

    public record Resumo(UUID id, String nome, String telefone, String email) {
        static Resumo de(Cliente cliente) {
            return new Resumo(cliente.getId(), cliente.getNome(), cliente.getTelefone(), cliente.getEmail());
        }
    }

    public record Pagina(List<Resumo> items, int page, int size, long totalElements, int totalPages) {
        static Pagina de(Page<Cliente> pagina) {
            return new Pagina(pagina.map(Resumo::de).getContent(), pagina.getNumber(), pagina.getSize(),
                    pagina.getTotalElements(), pagina.getTotalPages());
        }
    }
}
