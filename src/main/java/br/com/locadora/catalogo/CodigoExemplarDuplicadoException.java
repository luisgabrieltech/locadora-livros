package br.com.locadora.catalogo;

public class CodigoExemplarDuplicadoException extends RuntimeException {
    public CodigoExemplarDuplicadoException() {
        super("Já existe um exemplar com o código informado.");
    }
}
