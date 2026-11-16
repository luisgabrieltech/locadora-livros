CREATE TABLE locadora.livros (
    id UUID PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    autor VARCHAR(150) NOT NULL,
    isbn VARCHAR(13),
    CONSTRAINT ck_livros_titulo CHECK (char_length(trim(titulo)) BETWEEN 1 AND 200),
    CONSTRAINT ck_livros_autor CHECK (char_length(trim(autor)) BETWEEN 1 AND 150),
    CONSTRAINT ck_livros_isbn CHECK (isbn IS NULL OR isbn ~ '^([0-9]{9}[0-9X]|97[89][0-9]{10})$')
);

CREATE TABLE locadora.exemplares (
    id UUID PRIMARY KEY,
    livro_id UUID NOT NULL,
    codigo VARCHAR(50) NOT NULL,
    CONSTRAINT fk_exemplares_livro FOREIGN KEY (livro_id) REFERENCES locadora.livros(id),
    CONSTRAINT uk_exemplares_codigo UNIQUE (codigo),
    CONSTRAINT ck_exemplares_codigo CHECK (char_length(trim(codigo)) BETWEEN 1 AND 50)
);

CREATE INDEX idx_exemplares_livro ON locadora.exemplares(livro_id);
