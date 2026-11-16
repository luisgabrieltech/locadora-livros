package br.com.locadora.clientes;

public class CpfDuplicadoException extends RuntimeException {
    public CpfDuplicadoException() {
        super("Já existe um cliente com o CPF informado.");
    }
}
