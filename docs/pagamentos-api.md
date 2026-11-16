# PAG-001 — Quitação integral por item

Estado: implementada e validada em 16/11/2026. Maven verify aprovado nesta sessão: 106 testes, sem falhas, erros ou ignorados.

## Objetivo e escopo

O responsável registra valores já recebidos, quitando integralmente um ou mais itens devolvidos da mesma locação. Não há processamento bancário, parcelamento, pagamento parcial de item, estorno ou edição de pagamentos nesta entrega.

POST /api/v1/locacoes/{locacaoId}/pagamentos

```json
{
  "itens": ["UUID de um item devolvido da locação"]
}
```

O identificador enviado é o do item histórico da locação, não o do exemplar. Lista obrigatória e não vazia, sem nulos ou repetições. Todos os itens devem pertencer à locação, estar devolvidos e ainda não pagos.

## Regras e respostas

- Usar data atual do servidor no fuso de negócio. Não aceitar data, valor, parcelas ou outros campos enviados no corpo.
- O valor integral é o valorTotal já fixado na devolução. Não recalcular diárias, multas, preços ou saldo. É permitido pagar alguns itens e manter outros em aberto.
- Um lote é uma transação: qualquer item inválido impede todos os pagamentos enviados.
- Item ainda emprestado ou já pago: 409. Repetição não altera o registro anterior.
- Item de outra locação, item inexistente ou locação ausente: 404. UUID, JSON, lista ou data de negócio inconsistentes: 400.
- Não registrar pagamento anterior à devolução, inclusive se a configuração de relógio retroceder.
- Sucesso 200: locacaoId, dataPagamento, itens com itemId/exemplarId/valorPago, valorTotalPago do lote e locacaoEncerrada.
- GET da locação inclui encerrada, derivado de todos os itens devolvidos e pagos. Sem flag persistida redundante. Os campos existentes por item, incluindo dataPagamento, continuam disponíveis.
- A listagem paginada mantém seu resumo atual; consulte o detalhe para acompanhar itens e encerramento.
- O cliente só pode retirar novamente quando não houver nenhuma dívida de exemplar devolvido nem exemplar atrasado, considerando todas as suas locações. Quitar uma locação não libera automaticamente um cliente que ainda tenha outras pendências.
- Pagamento não altera disponibilidade, data de devolução nem valor da cobrança. Uma cópia já retirada por outro cliente permanece emprestada.
- HTTP Basic e CSRF obrigatórios, erros MVC/domínio em application/problem+json, seguindo o padrão existente.

## Concorrência e persistência

Consultar somente clienteId antes de bloquear o cliente; carregar itens depois da espera para não usar estado antigo. Bloquear exemplares por UUID ordenado, conforme ADR-005/006. Validar todo o lote antes de preencher dataPagamento.

Reutilizar o estado criado na V4, sem nova migração ou alteração de migrações aplicadas. Um item mantém uma única data de quitação. Nenhuma operação permite reabrir, excluir ou alterar um pagamento.

## Critérios e testes

1. Devolver, pagar dias depois e preservar o valor fixado; refletir dataPagamento no GET.
2. Quitar parte dos itens mantém dívida/bloqueio e encerrada=false. Quitar todos os itens devolvidos torna encerrada=true.
3. Não liberar cliente com atraso ou dívida em outra locação; depois de regularizar todas as pendências, permitir retirada.
4. Rejeitar item emprestado, repetido, pago, desconhecido ou de outra locação sem pagar parte do lote.
5. Rejeitar valores/datas/parcelas enviados; não aceitar pagamento parcial.
6. Pagamentos concorrentes do mesmo item resultam em 200 e 409. Lotes sobrepostos não quitam itens restantes do lote rejeitado.
7. Pagamento concorrente com nova retirada respeita o bloqueio do cliente. Pagamento de empréstimo antigo não altera a disponibilidade da nova retirada da cópia.
8. Validar Basic/CSRF e a regressão completa com PostgreSQL/Testcontainers e Clock controlado.

## Exemplo no PowerShell

Depois de obter $headers/$sessao do fluxo CSRF e registrar a devolução conforme [devolucoes-api.md](devolucoes-api.md), use $resultado retornado pela devolução e $locacao da abertura:

```powershell
$corpo = @{ itens = @($resultado.itens[0].itemId) } | ConvertTo-Json -Depth 3
$quitacao = Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/locacoes/' + $locacao.id + '/pagamentos') -Method Post -Headers $headers -WebSession $sessao -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($corpo))
$quitacao
Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/locacoes/' + $locacao.id) -Headers $headers
```

Envie mais IDs para quitar um lote da mesma locação. A devolução e o pagamento são operações distintas; pode-se registrar o pagamento imediatamente após devolver. Se houver dúvida sobre o resultado de uma chamada, consulte os itens antes de repetir: quitação já registrada retorna 409.

## Validação executada
Compilação, empacotamento e regressão aprovados com PostgreSQL 17.7/Testcontainers e Clock controlado: 50 testes no Surefire e 56 no Failsafe, incluindo 12 PagamentosIT. A fixture antiga de quitação em LocacoesIT foi substituída pelo endpoint real. Nenhuma migração nova foi necessária.
