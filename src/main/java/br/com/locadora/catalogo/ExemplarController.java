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
@RequestMapping("/api/v1/exemplares")
public class ExemplarController {
    private final ExemplarService service;

    public ExemplarController(ExemplarService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExemplarResponse> criar(@Valid @RequestBody ExemplarRequest dados) {
        var response = service.criar(dados);
        return ResponseEntity.created(URI.create("/api/v1/exemplares/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ExemplarResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @GetMapping
    public Pagina<ExemplarResponse> listar(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(page, size);
    }

    @PutMapping("/{id}")
    public ExemplarResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ExemplarRequest dados) {
        return service.atualizar(id, dados);
    }
}
