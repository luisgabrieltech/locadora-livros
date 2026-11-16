package br.com.locadora.clientes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;
import org.hibernate.validator.constraints.CodePointLength;

public record ClienteRequest(
        @NotBlank(message = "Informe o nome.")
        @CodePointLength(min = 2, max = 150, message = "O nome deve ter entre 2 e 150 caracteres.") String nome,
        @NotBlank(message = "Informe o telefone.")
        @Pattern(regexp = "[0-9]{10,15}", message = "Informe um telefone com 10 a 15 dígitos.") String telefone,
        @NotBlank(message = "Informe o CPF.")
        @Pattern(regexp = "[0-9]{11}", message = "Informe um CPF com 11 dígitos ou pontuação convencional.")
        @CPF(message = "Informe um CPF válido.") String cpf,
        @Size(max = 254, message = "O email deve ter no máximo 254 caracteres.")
        @Email(message = "Informe um email válido.") String email) {

    public ClienteRequest {
        nome = nome == null ? null : nome.strip();
        telefone = normalizarTelefone(telefone);
        cpf = normalizarCpf(cpf);
        email = email == null || email.isBlank() ? null : email.strip();
    }

    private static String normalizarTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String valor = telefone.strip();
        return valor.matches("[+0-9() \\-]+") ? valor.replaceAll("[^0-9]", "") : valor;
    }

    private static String normalizarCpf(String cpf) {
        if (cpf == null) {
            return null;
        }
        String valor = cpf.strip();
        return valor.matches("[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}")
                ? valor.replace(".", "").replace("-", "") : valor;
    }

    @Override
    public String toString() {
        return "ClienteRequest[dados pessoais omitidos]";
    }
}
