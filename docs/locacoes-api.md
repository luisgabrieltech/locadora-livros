# LOC-001 — Abertura e consulta de locações

Estado: abertura e consulta de locações implementadas e validadas em 16/11/2026. Maven verify aprovado nesta sessão: 74 testes, sem falhas, erros ou ignorados. Devoluções estão disponíveis na DEV-001; pagamento está disponível em PAG-001, por operação separada.

## Objetivo e contexto

Como responsável pela locadora, quero registrar a retirada de várias cópias por um cliente, com prazo por exemplar, garantindo disponibilidade e elegibilidade do cliente. Dependências: clientes e catálogo validados; relatórios da CAT-001 somam 60 testes aprovados.

## Escopo aprovado

| Método e caminho | Comportamento | Sucesso |
|---|---|---|
| POST /api/v1/locacoes | Abrir locação de um ou mais exemplares | 201, corpo e Location |
| GET /api/v1/locacoes/{id} | Consultar locação e itens | 200 |
| GET /api/v1/locacoes | Listar locações com paginação | 200 |

Usar HTTP Basic e CSRF já existentes, sem alterar sua política. Manter devolução e pagamento para DEV-001 e PAG-001; não expor cancelamento, exclusão, renovação ou alterações de locações nesta entrega.

## Regras já aprovadas

- Vários exemplares em uma locação, com previsão de devolução por item.
- Não permitir locações simultâneas da mesma cópia física.
- Bloquear novas locações se houver exemplar atrasado ou dívida de exemplar devolvido. Exemplar dentro do prazo, com pagamento previsto para a devolução, não constitui dívida para o bloqueio.
- Preservar preços vigentes na abertura: diária inicial R$ 2,00 e multa diária adicional R$ 1,00 por exemplar.
- Pagamento na devolução, com possibilidade de pendência; regras completas em requirements.md.

## Decisões aprovadas

1. Retirada corresponde à data atual do sistema no fuso America/Sao_Paulo. Não aceitar retirada retroativa ou agendamento de retirada futura no MVP.
2. Prazo por exemplar pode ser a própria data da retirada ou uma data posterior. Isso permite devolução no mesmo dia, com cobrança mínima já aprovada de uma diária. Datas além de 31/12/5874897, limite técnico de DATE no PostgreSQL, retornam 400; isso não define um prazo máximo comercial.
3. Abertura atômica: se qualquer item estiver indisponível, inexistente ou inválido, não registrar a locação nem retirar os demais exemplares. Rejeitar também uma lista vazia ou IDs repetidos.
4. Ao existir histórico de locação de um exemplar, impedir a troca do livro vinculado à cópia. Atualizações do código e dos metadados do título continuam seguindo o catálogo. Reavaliar preservação de descrições históricas quando necessário, sem apagar histórico.

## Corpo da requisição

```json
{
  "clienteId": "UUID do cliente",
  "itens": [
    {
      "exemplarId": "UUID da cópia",
      "dataPrevistaDevolucao": "2026-11-20"
    }
  ]
}
```

As strings de UUID acima são placeholders explicativos. Preços e data de retirada vêm do servidor; não aceitar preços informados pelo cliente da API. Datas ISO-8601, sem horário.

## Respostas e erros

- Resposta de abertura/consulta: id da locação, clienteId, dataRetirada e itens com id próprio, exemplarId, dataPrevistaDevolucao, valorDiaria e valorMultaDiaria preservados. Não incluir CPF ou dados de contato desnecessários.
- Listagem paginada com items, page, size, totalElements e totalPages, seguindo o padrão atual; ordenar por dataRetirada e id para estabilidade. Cada resumo contém id, clienteId, dataRetirada, encerrada, atrasada e pagamentoPendente; o detalhe contém os itens, ordenados por exemplarId. Filtros opcionais de situação/cliente em [consultas-locacoes-api.md](consultas-locacoes-api.md).
- 400: entrada malformada, datas inválidas ou fora da faixa persistível, prazo anterior à retirada, lista vazia ou exemplar repetido.
- 404: cliente, exemplar ou locação inexistente.
- 409: exemplar já emprestado, cliente bloqueado ou tentativa de trocar o livro de cópia com histórico.
- Erros de domínio/MVC em application/problem+json, sem dados pessoais ou SQL.

## Plano técnico e critérios de aceitação

1. Criar migração para locação e itens, com relacionamentos e preços monetários exatos; não alterar migrações já aplicadas.
2. Garantir uma única retirada ativa por exemplar no banco, com transação e controle de concorrência. Ordenar a aquisição de bloqueios de itens para evitar dependência da ordem enviada pelo cliente.
3. Implementar consulta de elegibilidade do cliente e indisponibilidade. A regra de dívida precisa estar integrada ao modelo antes de ser declarada pronta; cenários de devolução e pagamento serão exercitados de ponta a ponta nas respectivas entregas. Não simular conformidade com uma validação que sempre libera o cliente.
4. Atualizar consultas do catálogo para derivar disponibilidade das locações ativas, incluindo listagens; remover o retorno sempre disponível antes de permitir retiradas.
5. Proteger a correção de vínculo de exemplar com histórico, conforme a decisão 4 aprovada.
6. Validar retirada bem-sucedida com múltiplos itens e prazos distintos; preço preservado; retirada mínima no mesmo dia; atomicidade de falhas; bloqueio por atraso/dívida; consulta/paginação; autenticação e CSRF.
7. Testar com Clock fixo e PostgreSQL/Testcontainers. Duas retiradas concorrentes da mesma cópia devem resultar em uma criação e um conflito, sem locações parciais ou cópias duplamente retiradas.

## Limites de conclusão

Abertura, consulta, disponibilidade, bloqueios e concorrência validados com PostgreSQL real. A partir de DEV-001, devolução e geração de dívida também são exercitadas por HTTP. A quitação também é exercitada por HTTP desde PAG-001; os cenários de dívida e regularização são cobertos pelos fluxos reais.

## Configuração e persistência

Migração V4 cria locacoes e itens_locacao. Campos de estado de devolução/pagamento permitem distinguir retirada ativa de dívida; não são editáveis pela API de abertura. Não excluir ou alterar manualmente esses estados para simular devolução no banco de uso local.

Tarifas locais opcionais em .env: LOCACAO_DIARIA=2.00 e LOCACAO_MULTA_DIARIA=1.00. Valores validados, com até duas casas decimais, diária positiva e multa não negativa. Reiniciar a aplicação após alterar a configuração; somente novas locações recebem os novos valores. Itens existentes preservam as tarifas gravadas.

A ordem dos bloqueios e a integração entre módulos estão detalhadas no [ADR-005](decisions/005-locacoes-e-concorrencia.md).

## Exemplo no PowerShell

Com $headers e $sessao obtidos pelo fluxo CSRF de [clientes-api.md](clientes-api.md#exercitar-a-api-no-powershell), e $cliente/$exemplar contendo os objetos retornados pelos cadastros:

```powershell
$corpo = @{
    clienteId = $cliente.id
    itens = @(@{
        exemplarId = $exemplar.id
        dataPrevistaDevolucao = (Get-Date).AddDays(7).ToString('yyyy-MM-dd')
    })
} | ConvertTo-Json -Depth 4
$locacao = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/locacoes' -Method Post -Headers $headers -WebSession $sessao -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($corpo))
$locacao
Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/locacoes/' + $locacao.id) -Headers $headers
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/locacoes?page=0&size=20' -Headers $headers
Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/exemplares/' + $exemplar.id) -Headers $headers
```

O exemplar consultado estará indisponível. Repetir sua retirada retorna 409. Adicionar outros itens no corpo permite prazos diferentes; a API valida todos antes de efetivar a operação.
## Consulta após devolução
Os itens de GET /api/v1/locacoes/{id} agora incluem dataDevolucao, valorTotal e dataPagamento. O total só é fixado na devolução e não aumenta depois dela. Consulte [devolucoes-api.md](devolucoes-api.md) para registrar a operação. dataPagamento permanece null até quitação.

## Encerramento após quitação
GET da locação inclui encerrada, verdadeiro somente quando todos os itens estiverem devolvidos e pagos. A listagem inclui indicadores de encerramento, atraso e pagamento pendente. Registrar recebimento em [pagamentos-api.md](pagamentos-api.md); quitar esta locação não remove pendências de outras locações do mesmo cliente.
