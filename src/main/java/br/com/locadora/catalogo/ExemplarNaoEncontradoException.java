package br.com.locadora.catalogo;

public class ExemplarNaoEncontradoException extends RuntimeException {
    public ExemplarNaoEncontradoException() {
        super("Exemplar não encontrado.");
    }
}
