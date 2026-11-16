# DEV-001 — Devolução parcial e cálculo final

Estado: implementada e validada em 16/11/2026. Maven verify aprovado nesta sessão: 94 testes, sem falhas, erros ou ignorados.

## Objetivo e dependências

Permitir que o responsável registre a devolução de parte ou de todos os exemplares de uma locação, calcule o valor final por item e libere as cópias. LOC-001 já está validada com 74 testes. O registro de quitação usa a operação separada documentada em [pagamentos-api.md](pagamentos-api.md).

## Regras já aprovadas

- Contagem por data, no fuso de negócio, com no mínimo uma diária.
- Dias cobrados = max(1, devolução efetiva - retirada).
- Dias de atraso = max(0, devolução efetiva - previsão do item).
- Total = dias cobrados × diária preservada + dias de atraso × multa diária preservada.
- Não aplicar preços da configuração atual a itens antigos.
- A devolução libera o exemplar e fixa o valor devido, mesmo sem pagamento. Não acrescentar diárias ou multas depois da devolução.
- Outros itens continuam emprestados; cliente bloqueado por dívida/atraso pode devolver normalmente.
- Situação física e financeira são distintas. Todos os itens devolvidos não significam locação quitada.

## Contrato aprovado

POST /api/v1/locacoes/{locacaoId}/devolucoes

```json
{
  "itens": ["UUID do item da locação", "UUID de outro item da mesma locação"]
}
```

- Identificar os itens pelo id retornado na abertura, preservando a distinção entre o histórico de empréstimo e o exemplar físico.
- Data de devolução vem do Clock do servidor, sem lançamento retroativo/futuro. Não aceitar valores, datas ou confirmação de pagamento enviados nesse corpo.
- Lista não vazia, sem IDs repetidos; todos os itens devem pertencer à locação informada e ainda estar emprestados.
- Devolução do lote integral: uma falha impede a alteração de todos os itens enviados; itens não enviados não são modificados.
- Item já devolvido: 409, sem recalcular valores ou alterar datas, inclusive em chamadas repetidas ou concorrentes.
- Resposta 200: locacaoId, dataDevolucao e resultado por item, com itemId, exemplarId, diasCobrados, diasAtraso, valorDiarias, valorMulta e valorTotal.
- Atualizar também GET da locação para incluir dataDevolucao, valorTotal e dataPagamento por item, com null enquanto não aplicável. Preservar os campos atuais.
- 400 para entrada/JSON/UUID inválido ou lista vazia/repetida; 404 para locação/item inexistente ou item que não pertence à locação; 409 para item já devolvido.
- Manter Basic, CSRF, erros application/problem+json e proibição de logs com dados pessoais.

## Decisões aprovadas

1. Registrar a devolução na data atual do servidor.
2. Tratar o lote como operação integral, sem devolução parcial acidental em caso de falha.
3. Rejeitar nova devolução de item já devolvido com 409.
4. Nesta entrega, registrar o valor como pendente; a operação de [pagamento](pagamentos-api.md) quita integralmente cada item, inclusive logo após a devolução. A devolução sozinha não confirma recebimento.

## Plano técnico

1. Calcular valores com BigDecimal e datas com Clock fixável; validar limites de persistência sem arredondar silenciosamente.
2. Reutilizar os campos criados na V4; criar nova migração apenas se for necessária, sem editar migrações aplicadas.
3. Seguir ADR-005: bloquear cliente, depois exemplares em ordem de UUID. Reconsultar os itens e verificar seu estado dentro da transação antes de fixar a devolução.
4. Retirada e devolução concorrentes devem observar o mesmo protocolo: nenhuma cópia pode ficar livre indevidamente por devolução repetida de um empréstimo antigo.
5. Gravar data e valor final por item numa única transação. Não alterar dataPagamento nem liberar o bloqueio financeiro enquanto houver dívida.
6. Derivar disponibilidade e bloqueios dos estados persistidos existentes, sem flags duplicadas.

## Critérios de aceitação

- Mesmo dia: uma diária, sem multa se dentro do prazo.
- Retirada dia 10, previsão dia 12, devolução dia 14, diária R$ 2 e multa R$ 1: quatro diárias e duas multas, total R$ 10.
- Devolução antecipada usa os dias efetivamente decorridos, respeitando o mínimo de um dia.
- Alterar a configuração atual de tarifas não modifica os valores preservados na locação.
- Devolver um de três itens libera só aquela cópia e mantém os outros emprestados.
- Devolução com dívida deixa o valor fixo e o cliente bloqueado para novas retiradas; outros clientes podem retirar a cópia liberada.
- Repetição, concorrência e lote contendo item inválido não alteram datas/valores anteriores nem liberam cópias incorretamente.
- Cliente com atraso/dívida continua podendo devolver; os testes precisam exercitar esse fluxo por HTTP, substituindo as fixtures financeiras onde aplicável.
- Testes unitários do cálculo e integração com PostgreSQL, além da regressão completa de clientes, catálogo, locações e CSRF.

## Validação executada

Maven verify executado pelo agente com PostgreSQL 17.7/Testcontainers: 50 testes no Surefire e 44 no Failsafe, total 94 aprovados. Compilação, empacotamento e regressão completa aprovados. Cálculo com Clock controlado, devoluções concorrentes e retirada concorrente de cópia devolvida cobertos. Quitação implementada posteriormente em PAG-001; veja pagamentos-api.md.

## Exemplo no PowerShell

Use $headers/$sessao do fluxo CSRF e $locacao retornada pela abertura, conforme [locacoes-api.md](locacoes-api.md). O campo enviado é o id do item da locação, não o id do exemplar:

```powershell
$corpo = @{ itens = @($locacao.itens[0].id) } | ConvertTo-Json -Depth 3
$resultado = Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/locacoes/' + $locacao.id + '/devolucoes') -Method Post -Headers $headers -WebSession $sessao -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($corpo))
$resultado.itens | Format-Table itemId, diasCobrados, diasAtraso, valorDiarias, valorMulta, valorTotal
Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/locacoes/' + $locacao.id) -Headers $headers
```

Envie mais IDs no array para devolver várias cópias. A repetição retorna 409; o valor anterior não é recalculado. A consulta detalhada mostra dataDevolucao e valorTotal fixados; dataPagamento permanece null até registrar a [quitação](pagamentos-api.md). Mesmo com todos os exemplares devolvidos, a locação não está financeiramente quitada enquanto houver dívidas.

## Persistência e limites

Usar os campos existentes da V4, sem nova migração ou edição de migrações aplicadas. Calcular todos os itens antes de alterá-los, com BigDecimal e sem arredondamento silencioso. Valores calculados acima de R$ 9.999.999.999,99 ou datas inconsistentes são rejeitados com 400, sem gravar parte do lote.

O estado de um empréstimo devolvido é imutável por este endpoint. Uma cópia retirada novamente continua indisponível mesmo que alguém tente repetir a devolução do empréstimo antigo. Protocolo de concorrência em [ADR-006](decisions/006-devolucoes-atomicas.md).