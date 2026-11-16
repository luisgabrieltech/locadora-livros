# ADR-003 — Autenticação e datas para execução local

Estado: aprovado pelo usuário antes da implementação.

## Contexto

API de portfólio executada somente no computador do usuário, com um responsável operando. Não há frontend nem produção no escopo atual.

## Alternativas

- Sem autenticação: não demonstra controle de acesso aos dados e operações.
- HTTP Basic com operador configurado externamente: suficiente para a API local, com suporte direto do Spring Security.
- Sessão de login ou tokens: acrescentariam fluxos sem necessidade atual; reavaliar quando houver interface ou outros clientes.

## Decisão

- HTTP Basic, um operador em memória, credenciais obrigatórias fornecidas externamente e senha codificada na inicialização.
- API e publicação da porta do banco somente em loopback.
- Sem cadastro de operadores e sem JWT nesta entrega.
- Preservar proteção CSRF, pois HTTP Basic pode ser enviado automaticamente por navegadores. Projetar obtenção/envio de token antes de implementar endpoints de escrita.
- Clock injetável no fuso America/Sao_Paulo, configurável por APP_TIME_ZONE, como referência futura das datas de negócio.

## Consequências

- Acesso simples via ferramenta de requisições; nenhuma sessão de autenticação necessária.
- HTTP Basic não cifra credenciais; a configuração HTTP atual é exclusivamente local. Uma futura exposição exigirá revisão de transporte e segurança.
- Alterações de credenciais exigem reinício da aplicação.
- Testes devem cobrir rejeição de credenciais ausentes/incorretas, acesso válido e CSRF.
- Regras de cobrança continuarão por data; o Clock permitirá testes determinísticos quando essas regras forem implementadas.

## Detalhamento na CLI-001
GET /api/v1/csrf fornece token e sessão para POST/PUT. A autenticação usa RequestAttributeSecurityContextRepository e NullRequestCache, com gerenciamento implícito de sessão desativado. O cookie CSRF não autentica o operador. Isso evita que SessionManagementFilter trate cada chamada Basic como um novo login e invalide o token. Não combinar STATELESS com requireExplicitAuthenticationStrategy(true), combinação rejeitada pela versão utilizada. Basic continua exigido em todas as requisições. Se houver login com sessão no futuro, reavaliar explicitamente as estratégias de fixação de sessão e rotação de tokens.