# CAT-001 — Livros e exemplares

Estado: implementada e validada. Usuário informou sucesso; relatórios locais conferidos em 16/11/2026 somam 60 testes aprovados, sem falhas, erros ou ignorados.

## Objetivo e escopo

O responsável cadastra os títulos e identifica cada cópia física para as futuras locações. Dependência: CORE-001 e CLI-001 validadas. Não inclui exclusão, empréstimos, reservas, baixa por dano ou alterações nas regras de clientes.

| Método e caminho | Comportamento | Sucesso |
|---|---|---|
| POST /api/v1/livros | Cadastrar título | 201 e Location |
| GET /api/v1/livros/{id} | Consultar título | 200 |
| GET /api/v1/livros | Listar títulos | 200 |
| PUT /api/v1/livros/{id} | Atualizar título | 200 |
| POST /api/v1/exemplares | Cadastrar cópia vinculada a um livro | 201 e Location |
| GET /api/v1/exemplares/{id} | Consultar cópia | 200 |
| GET /api/v1/exemplares | Listar cópias | 200 |
| PUT /api/v1/exemplares/{id} | Atualizar cópia | 200 |

## Contratos e regras

- Identificadores UUID, gerados pelo servidor e imutáveis.
- Livro: titulo obrigatório (até 200 caracteres), autor obrigatório (até 150) e isbn opcional. Remover espaços nas extremidades de título/autor, preservando Unicode e nomes de um caractere.
- ISBN: aceitar ISBN-10 e ISBN-13 válidos, com verificação do dígito; aceitar separadores espaço/hífen e x final no ISBN-10, armazenando sem separadores e com X maiúsculo. Vazio equivale a ausência; letras inesperadas não são removidas silenciosamente. ISBN-13 deve começar por 978 ou 979. Não há regra aprovada de unicidade de ISBN; não criar essa restrição.
- Exemplar: livroId obrigatório e existente; codigo obrigatório, até 50 caracteres após remover espaços das extremidades. Código único global, incluindo entre livros diferentes, garantido por UNIQUE no banco. Preservar a grafia; comparação diferencia maiúsculas/minúsculas.
- PUT substitui os campos editáveis; omitir isbn remove seu valor. PUT de exemplar recebe livroId e codigo completos; o vínculo pode ser corrigido somente enquanto o exemplar não possuir histórico de locação. Com histórico, trocar livroId retorna 409; alterar o código continua permitido.
- Resposta de livro: id, titulo, autor, isbn. Resposta de exemplar: id, livroId, codigo, disponivel.
- A partir de LOC-001, disponivel é derivado das locações ativas: false se a cópia não foi devolvida; true caso contrário. Uma dívida de exemplar devolvido bloqueia o cliente, sem tornar a cópia indisponível. Não existe flag persistida redundante.
- Listagens: page inicial 0, size padrão 20, intervalo de tamanho 1–100. Envelope items, page, size, totalElements e totalPages. Ordenar livros por titulo/id e exemplares por codigo/id. Página além do fim contém items vazio.
- Dados/JSON/UUID/parâmetros inválidos: 400; livro ou exemplar inexistente: 404; código duplicado: 409. Erros MVC/domínio em application/problem+json, seguindo clientes.
- HTTP Basic obrigatório; escritas exigem também token e cookie obtidos em GET /api/v1/csrf. Não alterar a configuração de autenticação/CSRF já validada.

## Critérios de aceitação e testes preparados

1. Criar, consultar e atualizar livro; conferir normalização, UUID, Location e remoção de ISBN.
2. Aceitar ISBN opcional/ISBN-10/ISBN-13; rejeitar formato, dígitos verificadores, campos vazios e limites excedidos.
3. Cadastrar várias cópias do mesmo livro, cada uma com código distinto e disponível.
4. Rejeitar exemplar vinculado a livro inexistente, sem gravar ou alterar parcialmente os dados anteriores.
5. Atualizar exemplar sem acusar duplicidade do próprio código; rejeitar código de outro exemplar, inclusive de outro livro.
6. Duas criações simultâneas com o mesmo código resultam em 201 e 409, com apenas um registro. UNIQUE e FK também são exercitados diretamente no PostgreSQL do teste.
7. Consultas/alterações de IDs ausentes retornam 404; UUIDs malformados e paginação inválida retornam 400.
8. Listagens vazias, paginadas e repetidas respeitam envelope e ordenação estável.
9. Rejeitar leituras sem autenticação e escritas sem CSRF. Manter testes de clientes e reutilização do token aprovados.
10. Migração V3 aplicada e Hibernate validando o schema no PostgreSQL via Testcontainers; conferir também a suíte anterior.

## Exemplos no PowerShell

Primeiro inicie a API e obtenha $headers e $sessao conforme o [fluxo de autenticação/CSRF](clientes-api.md#exercitar-a-api-no-powershell). Não reutilize esses valores após reiniciar a aplicação.

```powershell
$corpo = Get-Content 'docs/examples/livro.json' -Raw -Encoding utf8
$livro = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/livros' -Method Post -Headers $headers -WebSession $sessao -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($corpo))
$copia = @{ livroId = $livro.id; codigo = 'EX-001' } | ConvertTo-Json
$exemplar = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/exemplares' -Method Post -Headers $headers -WebSession $sessao -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($copia))
Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/livros/' + $livro.id) -Headers $headers
Invoke-RestMethod -Uri ('http://127.0.0.1:8080/api/v1/exemplares/' + $exemplar.id) -Headers $headers
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/exemplares?page=0&size=20' -Headers $headers
```

Repetir o código EX-001 retorna 409. Nos PUT, use o caminho com id e o corpo completo, com os mesmos headers e sessão.

## Validação

- Sem Docker: .\mvnw.cmd test.
- Com Docker: .\mvnw.cmd clean verify.
- O banco pessoal do usuário não é usado nos testes. Não remover testes nem substituir PostgreSQL por banco em memória para contornar limitações da sessão do agente.

Referência da validação ISBN utilizada: [Hibernate Validator — ISBN.Type.ANY](https://docs.hibernate.org/validator/9.0/api/org/hibernate/validator/constraints/ISBN.Type.html).
