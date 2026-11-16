package br.com.locadora.locacoes;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record DevolucaoRequest(
        @NotEmpty(message = "Informe ao menos um item.") List<@NotNull UUID> itens) {

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("Campo não permitido na devolução.");
    }
}
