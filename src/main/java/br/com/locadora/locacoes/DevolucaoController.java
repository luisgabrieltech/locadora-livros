package br.com.locadora.locacoes;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locacoes/{locacaoId}/devolucoes")
public class DevolucaoController {
    private final DevolucaoService service;

    public DevolucaoController(DevolucaoService service) {
        this.service = service;
    }

    @PostMapping
    public DevolucaoResponse devolver(@PathVariable UUID locacaoId, @Valid @RequestBody DevolucaoRequest dados) {
        return service.devolver(locacaoId, dados);
    }
}
