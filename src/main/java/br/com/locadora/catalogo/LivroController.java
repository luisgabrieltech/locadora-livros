package br.com.locadora.catalogo;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/livros")
public class LivroController {
    private final LivroService service;

    public LivroController(LivroService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LivroResponse> criar(@Valid @RequestBody LivroRequest dados) {
        var response = service.criar(dados);
        return ResponseEntity.created(URI.create("/api/v1/livros/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public LivroResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @GetMapping
    public Pagina<LivroResponse> listar(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(page, size);
    }

    @PutMapping("/{id}")
    public LivroResponse atualizar(@PathVariable UUID id, @Valid @RequestBody LivroRequest dados) {
        return service.atualizar(id, dados);
    }
}
