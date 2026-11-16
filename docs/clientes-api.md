# CLI-001 — Cadastro de clientes

Estado: implementada e validada. Usuário confirmou sucesso do teste direcionado e do verify; relatórios locais conferidos em 16/11/2026: 35 testes aprovados, sem falhas, erros ou ignorados.

## Objetivo e contexto

Como responsável pela locadora, quero cadastrar, consultar e atualizar clientes para associá-los às futuras locações. Dependência: estrutura CORE-001, validada pelo usuário e pelos relatórios locais (11 testes aprovados).

## Escopo aprovado

| Método e caminho | Comportamento | Sucesso |
|---|---|---|
| POST /api/v1/clientes | Criar cliente | 201, corpo e Location |
| GET /api/v1/clientes/{id} | Consultar por identificador | 200 |
| GET /api/v1/clientes?page=0&size=20 | Listar com paginação | 200 |
| PUT /api/v1/clientes/{id} | Substituir os dados editáveis | 200 |

Sem exclusão, inativação ou pesquisa por CPF nesta entrega. Clientes não são usuários de autenticação.

## Dados e validações

- Identificador gerado pelo servidor, do tipo UUID, imutável.
- nome: obrigatório, de 2 a 150 caracteres após remover espaços das extremidades. Não exigir sobrenome ou restringir a letras ASCII.
- telefone: obrigatório; aceitar dígitos e formatação comum (+, espaços, parênteses e hífen), armazenar apenas dígitos, de 10 a 15 dígitos. Não pressupor que a validação confirma a existência do telefone.
- cpf: obrigatório, string de 11 dígitos após normalização; validar dígitos verificadores e rejeitar sequências iguais. Aceitar entrada numérica ou no formato pontuado convencional; não remover letras silenciosamente. Preservar zeros à esquerda.
- email: opcional, no máximo 254 caracteres e formato válido quando informado. Valor vazio vira ausência; não exigir unicidade.
- O PUT recebe todos os campos obrigatórios; omitir email remove o valor anterior. Atualizar CPF é permitido se válido e não atribuído a outro cliente, conforme requisitos existentes.
- Respostas de detalhe/criação/atualização: id, nome, telefone, cpf e email. Listagem: id, nome, telefone e email, sem CPF.
- Listagem com items, page, size, totalElements e totalPages; página inicial 0, tamanho padrão 20, máximo 100, ordenação estável por nome e id. Página além do fim retorna items vazio; parâmetros negativos ou tamanho fora do intervalo retornam 400.

## Consistência, erros e segurança

- Migração V2 cria locadora.clientes, com colunas obrigatórias e restrição UNIQUE para CPF normalizado. Garantir unicidade também sob requisições concorrentes.
- Erros padronizados em application/problem+json: 400 para dados inválidos/JSON malformado, 404 para cliente inexistente, 409 para CPF duplicado. Incluir mensagens por campo sem repetir valores sensíveis, SQL ou stack trace.
- HTTP Basic continua obrigatório. Acrescentar GET /api/v1/csrf, autenticado, que fornece token e nome do header; preservar cookie de sessão CSRF e enviar token em POST/PUT. Credenciais Basic continuam necessárias em cada requisição; a sessão guarda somente o estado CSRF.
- Sem credenciais em leitura: 401. Credenciais válidas e token ausente/inválido na escrita: 403. Sem token, a rejeição CSRF pode ocorrer antes da autenticação.
- Não incluir CPF, senha ou corpo de cadastro em logs.

## Critérios de aceitação e validação

1. Cadastrar dados válidos e consultar o cliente persistido; conferir 201, Location e dados normalizados.
2. Rejeitar campos ausentes, CPF inválido, telefone inválido e email malformado com 400, sem persistência parcial.
3. Rejeitar CPF duplicado, inclusive com formatação diferente e em concorrência, com 409 e apenas um registro persistido.
4. Atualizar o próprio cliente sem tratar seu CPF como duplicado; impedir uso do CPF de outro cliente.
5. Consultar/alterar identificador inexistente retorna 404; UUID malformado retorna 400.
6. Listagem vazia e paginada segue contrato; CPF não é exposto na listagem.
7. Rejeitar leitura anônima, escrita com credenciais inválidas e token válido, e escrita sem token válido. Aceitar escrita com autenticação e fluxo CSRF completo.
8. Migração e restrições verificadas em PostgreSQL real via Testcontainers; manter testes anteriores aprovados.

## Implementação incremental

1. Migração e modelo de cliente; normalização e validação, com testes dos casos de borda.
2. Persistência e serviço com tratamento de duplicidade e inexistência.
3. Endpoints, DTOs, paginação e tratamento de erros.
4. Fluxo CSRF, testes HTTP/integração e exemplos de execução.

Contrato, limites de campos e fluxo CSRF aprovados pelo usuário antes da implementação.

## Exercitar a API no PowerShell

Com a aplicação e o PostgreSQL em execução, obtenha as credenciais interativamente (não coloque senhas no histórico):

```powershell
$credencial = Get-Credential -UserName operador -Message 'Operador local da locadora'
$par = $credencial.UserName + ':' + $credencial.GetNetworkCredential().Password
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($par)) }
$csrf = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/csrf' -Headers $headers -SessionVariable sessao
$headers[$csrf.headerName] = $csrf.token
$corpo = Get-Content 'docs/examples/cliente.json' -Raw -Encoding utf8
$cliente = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/clientes' -Method Post -Headers $headers -WebSession $sessao -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($corpo))
$cliente
Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/clientes/' + $cliente.id) -Headers $headers
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/clientes?page=0&size=20' -Headers $headers
```

O exemplo usa dados sintéticos e CPF calculado apenas para demonstração. Uma segunda criação com esse CPF deve retornar 409. Para atualizar, use PUT no caminho com o id, os mesmos headers/sessão e um corpo completo; omitir email remove o valor anterior. Não registre ou compartilhe os headers, cookie, token ou credenciais. Ao reiniciar a API, obtenha um novo token.

O endpoint de token usa Cache-Control: no-store. A sessão CSRF não substitui HTTP Basic: cada chamada continua exigindo credenciais, e POST/PUT também exigem token e cookie correspondentes. Erros de autenticação/CSRF ocorrem nos filtros de segurança; erros de domínio e validação MVC usam application/problem+json.