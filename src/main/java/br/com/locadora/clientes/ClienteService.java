package br.com.locadora.clientes;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClienteService {

    private final ClienteRepository repository;

    public ClienteService(ClienteRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ClienteResponse criar(ClienteRequest dados) {
        if (repository.existsByCpf(dados.cpf())) {
            throw new CpfDuplicadoException();
        }
        // O UNIQUE no banco protege também o intervalo entre a consulta e a gravação.
        return ClienteResponse.de(repository.saveAndFlush(new Cliente(dados)));
    }

    public ClienteResponse buscar(UUID id) {
        return ClienteResponse.de(encontrar(id));
    }

    public ClienteResponse.Pagina listar(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new PaginacaoInvalidaException();
        }
        var pagina = PageRequest.of(page, size, Sort.by("nome", "id"));
        return ClienteResponse.Pagina.de(repository.findAll(pagina));
    }

    @Transactional
    public ClienteResponse atualizar(UUID id, ClienteRequest dados) {
        var cliente = encontrar(id);
        if (repository.existsByCpfAndIdNot(dados.cpf(), id)) {
            throw new CpfDuplicadoException();
        }
        cliente.atualizar(dados);
        return ClienteResponse.de(repository.saveAndFlush(cliente));
    }

    private Cliente encontrar(UUID id) {
        return repository.findById(id).orElseThrow(ClienteNaoEncontradoException::new);
    }
}
