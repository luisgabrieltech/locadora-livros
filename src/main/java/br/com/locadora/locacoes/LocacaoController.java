package br.com.locadora.locacoes;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locacoes")
public class LocacaoController {
    private final LocacaoService service;

    public LocacaoController(LocacaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LocacaoResponse> abrir(@Valid @RequestBody LocacaoRequest dados) {
        var locacao = service.abrir(dados);
        return ResponseEntity.created(URI.create("/api/v1/locacoes/" + locacao.id())).body(locacao);
    }

    @GetMapping("/{id}")
    public LocacaoResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @GetMapping
    public LocacaoResponse.Pagina listar(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) SituacaoLocacao situacao) {
        return service.listar(page, size, clienteId, situacao);
    }
}
