package br.com.locadora.locacoes;

public class ItemLocacaoNaoEncontradoException extends RuntimeException {
    public ItemLocacaoNaoEncontradoException() {
        super("Item não encontrado nesta locação.");
    }
}
