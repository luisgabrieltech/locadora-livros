CREATE TABLE locadora.clientes (
    id UUID PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    telefone VARCHAR(15) NOT NULL,
    cpf VARCHAR(11) NOT NULL,
    email VARCHAR(254),
    CONSTRAINT uk_clientes_cpf UNIQUE (cpf),
    CONSTRAINT ck_clientes_cpf_formato CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT ck_clientes_telefone_formato CHECK (telefone ~ '^[0-9]{10,15}$'),
    CONSTRAINT ck_clientes_nome_tamanho CHECK (char_length(trim(nome)) BETWEEN 2 AND 150)
);
