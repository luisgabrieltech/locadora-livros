package br.com.locadora.locacoes;

public class LocacaoInvalidaException extends RuntimeException {
    public LocacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
