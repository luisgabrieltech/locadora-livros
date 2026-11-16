package br.com.locadora.locacoes;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LocacaoRequest(
        @NotNull(message = "Informe o cliente.") UUID clienteId,
        @NotEmpty(message = "Informe ao menos um exemplar.") List<@NotNull @Valid Item> itens) {

    @JsonAnySetter
    public void rejeitarCampoExtra(String nome, Object valor) {
        throw new IllegalArgumentException("Campo não permitido na abertura de locação.");
    }

    public record Item(
            @NotNull(message = "Informe o exemplar.") UUID exemplarId,
            @NotNull(message = "Informe o prazo de devolução.") LocalDate dataPrevistaDevolucao) {
        @JsonAnySetter
        public void rejeitarCampoExtra(String nome, Object valor) {
            throw new IllegalArgumentException("Campo não permitido no item de locação.");
        }
    }
}
