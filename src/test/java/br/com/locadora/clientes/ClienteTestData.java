package br.com.locadora.clientes;

// CPFs calculados apenas para fixtures; não usar dados de clientes reais.
final class ClienteTestData {
    private ClienteTestData() {}

    static String cpf(int seed) {
        String base = "%09d".formatted(seed);
        String result = base;
        for (int length = 9; length <= 10; length++) {
            int sum = 0;
            for (int i = 0; i < length; i++) {
                sum += (result.charAt(i) - '0') * (length + 1 - i);
            }
            int digit = 11 - sum % 11;
            result += digit >= 10 ? "0" : Integer.toString(digit);
        }
        return result;
    }

    static String formatar(String cpf) {
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }
}
