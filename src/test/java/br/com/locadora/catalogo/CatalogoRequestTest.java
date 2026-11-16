package br.com.locadora.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogoRequestTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void fechar() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"978-0-306-40615-7", "0 306 40615 2", "0-8044-2957-x"})
    void aceitaIsbn10Ou13ComSeparadores(String isbn) {
        var dados = new LivroRequest("  Título  ", "  Autor  ", isbn);
        assertThat(VALIDATOR.validate(dados)).isEmpty();
        assertThat(dados.titulo()).isEqualTo("Título");
        assertThat(dados.autor()).isEqualTo("Autor");
        assertThat(dados.isbn()).doesNotContain(" ", "-").doesNotContain("x");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void isbnOpcional(String isbn) {
        var dados = new LivroRequest("Livro", "Autor", isbn);
        assertThat(VALIDATOR.validate(dados)).isEmpty();
        assertThat(dados.isbn()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9780306406158", "0306406153", "abc9780306406157", "978.0306406157", "123", "0000000000000"})
    void rejeitaIsbnInvalido(String isbn) {
        assertThat(VALIDATOR.validate(new LivroRequest("Livro", "Autor", isbn)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("isbn"));
    }

    @Test
    void validaCamposObrigatoriosELimitesUnicode() {
        assertThat(VALIDATOR.validate(new LivroRequest(" ", null, null)))
                .extracting(v -> v.getPropertyPath().toString()).contains("titulo", "autor");
        assertThat(VALIDATOR.validate(new LivroRequest("a".repeat(201), "a".repeat(151), null)))
                .extracting(v -> v.getPropertyPath().toString()).contains("titulo", "autor");
        assertThat(VALIDATOR.validate(new LivroRequest("📚".repeat(200), "李".repeat(150), null))).isEmpty();
    }

    @Test
    void codigoObrigatorioELivroObrigatorio() {
        assertThat(VALIDATOR.validate(new ExemplarRequest(null, " ")))
                .extracting(v -> v.getPropertyPath().toString()).contains("livroId", "codigo");
    }

    @Test
    void normalizaCodigoEValidaLimite() {
        var valido = new ExemplarRequest(UUID.randomUUID(), "  EX-001  ");
        assertThat(VALIDATOR.validate(valido)).isEmpty();
        assertThat(valido.codigo()).isEqualTo("EX-001");
        assertThat(VALIDATOR.validate(new ExemplarRequest(UUID.randomUUID(), "a".repeat(50)))).isEmpty();
        assertThat(VALIDATOR.validate(new ExemplarRequest(UUID.randomUUID(), "a".repeat(51))))
                .anyMatch(v -> v.getPropertyPath().toString().equals("codigo"));
    }

    @Test
    void normalizaCodigoSemAlterarIdentidade() {
        var dados = new ExemplarRequest(UUID.randomUUID(), "  EX-001  ");
        assertThat(dados.codigo()).isEqualTo("EX-001");
        assertThat(VALIDATOR.validate(dados)).isEmpty();
        assertThat(VALIDATOR.validate(new ExemplarRequest(UUID.randomUUID(), "a".repeat(51))))
                .anyMatch(v -> v.getPropertyPath().toString().equals("codigo"));
    }
}
