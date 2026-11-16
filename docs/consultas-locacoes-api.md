# REV-003 — Consultas operacionais de locações

Estado: contrato autorizado pelo usuário, implementado e validado em 16/11/2026.

## Objetivo e contexto

Permitir ao responsável localizar locações abertas, exemplares atrasados, dívidas e histórico de um cliente sem consultar cada locação individualmente. Depende das regras RN-10 a RN-14 e dos fluxos de abertura, devolução e pagamento existentes.

## Contrato

Estender GET /api/v1/locacoes, preservando page (padrão 0), size (padrão 20, máximo 100), envelope e ordenação atual por dataRetirada/id.

| Parâmetro opcional | Semântica |
|---|---|
| situacao=ABERTA | Pelo menos um exemplar ainda não devolvido ou devolvido sem pagamento; equivale a encerrada=false |
| situacao=ATRASADA | Pelo menos um exemplar ainda não devolvido com previsão anterior à data atual |
| situacao=PAGAMENTO_PENDENTE | Pelo menos um exemplar devolvido com valor devido e ainda não pago |
| situacao=ENCERRADA | Todos os exemplares devolvidos e pagos |
| clienteId=UUID | Apenas locações desse cliente, combinável com situacao |

Sem filtros, continuar retornando todo o histórico paginado. Sem situacao, clienteId permite consultar o histórico completo de um cliente. Cliente inexistente com UUID válido retorna página vazia; UUID, situação ou paginação inválidos retornam 400 em application/problem+json. Valores de situacao são exatamente os apresentados, em maiúsculas; o filtro seleciona uma situação por consulta nesta entrega.

Os filtros representam recortes que podem se sobrepor: ATRASADA e PAGAMENTO_PENDENTE pertencem a ABERTA; uma locação com devolução parcial pode ser ambas. Uma locação com tudo devolvido e dívida continua ABERTA, mas não ATRASADA.

Adicionar ao resumo os campos booleanos encerrada, atrasada e pagamentoPendente, mantendo id, clienteId e dataRetirada. Não expor CPF/contato, valores financeiros estimados ou lista de itens no resumo. Preservar o contrato do GET de detalhe.

Exemplos:

```http
GET /api/v1/locacoes?situacao=ATRASADA&page=0&size=20
GET /api/v1/locacoes?situacao=PAGAMENTO_PENDENTE&clienteId=<UUID>
GET /api/v1/locacoes?clienteId=<UUID>
```

## Abordagem técnica

- Derivar a situação dos itens existentes; nenhuma coluna de status, tabela ou migração nova.
- Capturar a data de negócio uma vez por consulta, usando o Clock e o fuso configurados.
- Aplicar filtros e paginação no banco. Evitar joins que dupliquem locações e prejudiquem a contagem; não filtrar em memória após paginar.
- Obter os indicadores da página sem carregar todas as coleções de itens nem consultar cada linha individualmente. Escolher a consulta/projeção mais simples ao implementar.
- Preservar autenticação HTTP Basic, isolamento dos dados pessoais e validações atuais. GET não altera dados nem exige token CSRF.

## Critérios de aceitação e validação

1. Sem filtros, preservar a listagem paginada e os campos anteriores; os novos indicadores refletem cada locação.
2. No dia previsto, um item não está atrasado; no dia seguinte passa a estar, respeitando America/Sao_Paulo.
3. Devolver o único exemplar atrasado remove o atraso mesmo que reste dívida; outros itens dentro do prazo não tornam a locação atrasada.
4. Devoluções parciais podem gerar simultaneamente atraso em um item e dívida em outro.
5. Tudo devolvido e não quitado pertence a ABERTA/PAGAMENTO_PENDENTE; após quitação integral pertence a ENCERRADA.
6. clienteId e situacao combinam por interseção, sem misturar histórico de outros clientes.
7. Vários itens na mesma locação não duplicam linhas nem totalElements. Cobrir pelo menos duas páginas filtradas.
8. Filtros inválidos retornam 400; nenhum resultado retorna 200 com items vazio e totalElements=0.
9. Requisição sem autenticação retorna 401; respostas não contêm CPF ou contato.
10. Executar testes de integração HTTP/PostgreSQL com relógio controlado e Maven verify completo.

## Entregas

1. Implementar filtros, consulta paginada e indicadores sem modificar gravações.
2. Adicionar regressões de datas, devolução parcial, dívida, encerramento e paginação.
3. Executar verify, revisar e atualizar documentação/estado da REV-003 com resultados reais.

Fora desta entrega: exportação, relatórios financeiros, busca por nome/CPF, múltiplas situações na mesma requisição, alterações de histórico, telas e Git/CI.

## Resultado da validação

- Implementados situacao/clienteId e os três indicadores do resumo. GET de detalhe e operações de gravação preservados. SQL agregada/projeção documentadas no ADR-008.
- Maven verify offline com JDK 22, cache .sandbox-m2/repository e PostgreSQL 17.7 descartável via Docker: BUILD SUCCESS, 53 Surefire + 60 Failsafe = 113 testes, sem falhas, erros ou ignorados. Log: target/consultas-verify.log.
- Após ampliar a regressão de devolução parcial para manter um terceiro exemplar dentro do prazo, executado novamente PagamentosIT via Maven -Dtest=PagamentosIT test: 13 testes aprovados, BUILD SUCCESS. Log: target/consultas-parcial-test.log. Essa repetição não adiciona testes distintos ao total de 113.
- Cobertos atraso por data/fuso, coexistência atraso/dívida, retirada do atraso mantendo item no prazo e dívida, quitação/encerramento, filtros combinados, páginas sem duplicação, validações e acesso protegido. Nenhum acesso ao PostgreSQL pessoal.
- REV-004 (avisos Hikari de containers encerrados na suíte completa) continua pendente; nenhuma mudança de infraestrutura de testes nesta entrega. Não foram realizados benchmarks nem análise de plano de execução.