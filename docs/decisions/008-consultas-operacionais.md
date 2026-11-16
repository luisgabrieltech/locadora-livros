# ADR-008 — Consultas operacionais de locações

Estado: contrato autorizado pelo usuário em 16/11/2026; implementado e validado com 113 testes na suíte e regressão complementar de devolução parcial.

## Contexto e alternativas

O operador precisa consultar abertas, atrasadas, dívidas e histórico por cliente. Devolução e quitação são independentes, e as situações podem se sobrepor. Uma coluna de status duplicaria informações dos itens. Carregar todas as locações para filtrar em memória prejudicaria paginação e contagem; carregar os itens de cada resumo adicionaria dados e consultas desnecessárias.

## Decisão

- Estender a listagem existente com filtros opcionais situacao e clienteId, combinados por interseção. Sem filtros, preservar o histórico completo paginado.
- ABERTA significa não encerrada: ainda há devolução ou quitação pendente. ATRASADA requer exemplar não devolvido com prazo anterior à data de negócio. PAGAMENTO_PENDENTE requer item devolvido, com valor devido e ainda não pago. ENCERRADA exige todos devolvidos e pagos.
- Derivar encerrada, atrasada e pagamentoPendente no resumo, sem persistir um status e sem modificar o detalhe ou os fluxos de gravação.
- Usar consulta SQL nativa agregada por locação com projeção dos campos do resumo. Aplicar filtro e paginação no PostgreSQL, com consulta de contagem correspondente. Capturar a data de negócio uma vez por chamada.
- Manter a ordenação dataRetirada/id e as regras de segurança existentes.

## Consequências

- Vários itens não duplicam linhas/paginação. Não há consulta de itens por resumo nem coleção de itens carregada para a listagem.
- A SQL usa bool_or/bool_and do PostgreSQL, já escolhido para o projeto. Uma troca de banco exigiria adaptar essa consulta.
- A agregação pode examinar vários itens para calcular os indicadores. Índices ou outra estratégia serão avaliados se medições do volume real justificarem; não há benchmark de desempenho nesta entrega.
- Página e total são leituras distintas no isolamento atual READ_COMMITTED e podem refletir mudanças concorrentes entre os comandos; não se promete um snapshot imutável da navegação.

Contrato e critérios em [consultas-locacoes-api.md](../consultas-locacoes-api.md).
