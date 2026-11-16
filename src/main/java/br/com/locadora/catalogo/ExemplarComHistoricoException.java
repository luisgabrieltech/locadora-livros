package br.com.locadora.catalogo;

public class ExemplarComHistoricoException extends RuntimeException {
    public ExemplarComHistoricoException() {
        super("Não é permitido trocar o livro de um exemplar com histórico de locação.");
    }
}
