package br.com.locadora.locacoes;

public class LocacaoConflitoException extends RuntimeException {
    public LocacaoConflitoException(String mensagem) {
        super(mensagem);
    }
}
