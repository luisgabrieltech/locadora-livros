package br.com.locadora.clientes;

public class PaginacaoInvalidaException extends RuntimeException {
    public PaginacaoInvalidaException() {
        super("A página deve ser não negativa e o tamanho deve estar entre 1 e 100.");
    }
}
