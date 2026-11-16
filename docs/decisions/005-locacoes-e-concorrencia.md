# ADR-005 — Consistência da abertura de locações

Estado: implementação das regras aprovadas para LOC-001.

## Contexto e alternativas

Uma locação retira várias cópias de forma integral. Verificar disponibilidade apenas antes de gravar permite que duas requisições retirem a mesma cópia. Uma flag disponível no exemplar criaria um segundo estado que poderia divergir do histórico. Locks apenas na memória do processo não protegem outras instâncias da aplicação.

## Decisão

- Persistir locação e itens, com preços monetários NUMERIC(12,2) copiados da configuração na abertura. A data de retirada vem do Clock no fuso de negócio; não aceitar data/preço do cliente da API.
- Uma transação para validação e gravação de todos os itens. Bloquear primeiro o cliente e depois cada exemplar por UUID em ordem crescente, independentemente da ordem do corpo HTTP.
- Após adquirir todos esses bloqueios, capturar uma única data efetiva para revalidar os prazos, consultar a elegibilidade e registrar a retirada. A validação inicial continua rejeitando entradas inválidas antes dos locks; sua data não é reutilizada após a espera (REV-001).
- Usar bloqueio pessimista de escrita nas linhas dos clientes/exemplares. O índice único parcial uk_itens_exemplar_ativo impede dois itens não devolvidos do mesmo exemplar mesmo em gravações que não passem pelo serviço.
- Consultar atraso ou dívida depois de bloquear o cliente. Campos mínimos data_devolucao, valor_total e data_pagamento distinguem empréstimo ativo de dívida de exemplar devolvido; sem endpoints para preencher esses campos nesta entrega.
- O catálogo deriva disponibilidade em consulta de lote, por meio de HistoricoExemplares, interface pertencente ao próprio catálogo. O módulo de locações implementa o contrato; evitar dependência direta do catálogo em repositórios de locações.
- Atualização de exemplar adquire o mesmo bloqueio usado na retirada e não permite mudar o livro depois de existir histórico. Código e metadados continuam editáveis conforme as regras existentes.

## Consequências

- Falha de qualquer item desfaz a operação inteira. Disputa por uma cópia gera uma criação e um conflito, sem locações parciais.
- Retiradas do mesmo cliente são serializadas; simplicidade adequada ao volume local atual. Não há garantia de ordem de chegada entre requisições concorrentes.
- Listagem do catálogo consulta todos os IDs emprestados da página de uma vez, sem uma consulta de disponibilidade por linha.
- DEV-001 e PAG-001 devem usar a mesma ordem de bloqueios (cliente antes de exemplares) e atualizar seus estados na mesma transação. A elegibilidade financeira já é consultada, mas seu fluxo HTTP completo depende dessas entregas.
- Alterações de tarifa exigem reiniciar a aplicação. Valores anteriores continuam nos itens já gravados; não recalcular preços históricos com a configuração corrente.
- Novos fluxos de login/sessão ou de cancelamento exigem novo alinhamento; não alterar a política Basic/CSRF como parte desta implementação.

Referências: [bloqueios no Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) e [índices únicos parciais no PostgreSQL](https://www.postgresql.org/docs/17/indexes-partial.html).
