package br.com.locadora.clientes;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClienteRequestTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @Test
    void normalizaSemPerderZerosOuAcentos() {
        String cpf = ClienteTestData.cpf(1);
        var dados = new ClienteRequest("  Éa  ", "+55 (11) 99999-0000",
                ClienteTestData.formatar(cpf), "  exemplo@example.test  ");
        assertThat(VALIDATOR.validate(dados)).isEmpty();
        assertThat(dados.nome()).isEqualTo("Éa");
    }

    @Test
    void aceitaNomeUnicodeEEmailAusente() {
        String cpf = ClienteTestData.cpf(1);
        var dados = new ClienteRequest("  李明  ", "+55 (11) 99999-0000",
                ClienteTestData.formatar(cpf), "  ");
        assertThat(VALIDATOR.validate(dados)).isEmpty();
        assertThat(dados.nome()).isEqualTo("李明");
        assertThat(dados.telefone()).isEqualTo("5511999990000");
        assertThat(dados.cpf()).isEqualTo(cpf).startsWith("0");
        assertThat(dados.email()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "11111111111", "123", "00000000190", "abc00000000191", "000.000001-91"})
    void rejeitaCpfInvalido(String cpf) {
        var dados = new ClienteRequest("Cliente Teste", "11999990000", cpf, null);
        assertThat(VALIDATOR.validate(dados)).anyMatch(v -> v.getPropertyPath().toString().equals("cpf"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "1234567890123456", "11999990000abc", "(11) 99999/0000"})
    void rejeitaTelefoneInvalido(String telefone) {
        var dados = new ClienteRequest("Cliente Teste", telefone, ClienteTestData.cpf(1), null);
        assertThat(VALIDATOR.validate(dados)).anyMatch(v -> v.getPropertyPath().toString().equals("telefone"));
    }

    @Test
    void aplicaLimitesDeNomeEEmail() {
        var dados = new ClienteRequest("a".repeat(151), "11999990000",
                ClienteTestData.cpf(1), "email-invalido");
        assertThat(VALIDATOR.validate(dados)).extracting(v -> v.getPropertyPath().toString())
                .contains("nome", "email");
        assertThat(VALIDATOR.validate(new ClienteRequest("a", "11999990000", ClienteTestData.cpf(1), null)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("nome"));
    }
}
