# ADR-004 — Separar livros de exemplares

Estado: direção aprovada pelo usuário no escopo CAT-001.

## Contexto e alternativas

A locadora pode possuir várias cópias físicas do mesmo título. Uma tabela única de livros/cópias duplicaria os metadados; um saldo numérico não identificaria qual cópia foi retirada ou devolvida.

## Decisão

Livro representa título, autor e ISBN opcional. Exemplar representa cada cópia física, com UUID, código único global e vínculo obrigatório a um livro. Persistir duas tabelas com FK e UNIQUE, sem exclusão de registros ou cascata destrutiva nesta entrega.

Disponibilidade será derivada das locações ativas quando essa funcionalidade existir. Até lá, todos os exemplares cadastrados estão disponíveis; não manter um segundo estado persistido que possa divergir das futuras locações.

## Consequências

- Várias cópias compartilham os metadados do título e mantêm identidade própria.
- Restrição de código protege inclusive concorrência; FK impede cópias sem livro.
- ISBN não identifica uma cópia física e não substitui o código interno. Não foi definida regra de unicidade de ISBN entre registros de livros.
- Ao implementar locações, revisar alterações de vínculo de exemplares para preservar o histórico, e substituir a disponibilidade inicial pela regra real antes de aceitar retiradas.
