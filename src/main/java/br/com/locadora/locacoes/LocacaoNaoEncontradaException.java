package br.com.locadora.locacoes;

public class LocacaoNaoEncontradaException extends RuntimeException {
    public LocacaoNaoEncontradaException() {
        super("Locação não encontrada.");
    }
}
