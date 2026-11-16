package br.com.locadora.catalogo;

public class LivroNaoEncontradoException extends RuntimeException {
    public LivroNaoEncontradoException() {
        super("Livro não encontrado.");
    }
}
