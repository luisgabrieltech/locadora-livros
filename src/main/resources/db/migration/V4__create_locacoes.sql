CREATE TABLE locadora.locacoes (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES locadora.clientes(id),
    data_retirada DATE NOT NULL
);
CREATE INDEX idx_locacoes_cliente ON locadora.locacoes(cliente_id);

CREATE TABLE locadora.itens_locacao (
    id UUID PRIMARY KEY,
    locacao_id UUID NOT NULL REFERENCES locadora.locacoes(id),
    exemplar_id UUID NOT NULL REFERENCES locadora.exemplares(id),
    data_prevista_devolucao DATE NOT NULL,
    valor_diaria NUMERIC(12,2) NOT NULL CHECK (valor_diaria > 0),
    valor_multa_diaria NUMERIC(12,2) NOT NULL CHECK (valor_multa_diaria >= 0),
    -- Estado mínimo necessário para distinguir retirada ativa de dívida.
    -- DEV-001/PAG-001 implementarão as operações que preenchem estes campos.
    data_devolucao DATE,
    valor_total NUMERIC(12,2),
    data_pagamento DATE,
    CONSTRAINT uk_locacao_exemplar UNIQUE (locacao_id, exemplar_id),
    CONSTRAINT ck_itens_estado_financeiro CHECK (
        (data_devolucao IS NULL AND valor_total IS NULL AND data_pagamento IS NULL)
        OR (data_devolucao IS NOT NULL AND valor_total IS NOT NULL AND valor_total >= 0)
    ),
    CONSTRAINT ck_itens_data_pagamento CHECK (
        data_pagamento IS NULL OR data_pagamento >= data_devolucao
    )
);
CREATE INDEX idx_itens_locacao ON locadora.itens_locacao(locacao_id);
CREATE INDEX idx_itens_exemplar_historico ON locadora.itens_locacao(exemplar_id);
CREATE UNIQUE INDEX uk_itens_exemplar_ativo ON locadora.itens_locacao(exemplar_id)
    WHERE data_devolucao IS NULL;
