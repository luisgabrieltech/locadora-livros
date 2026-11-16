# ADR-001 — Arquitetura inicial da API

Estado: aprovado pelo usuário.

## Contexto
Locadora de livros operada por um responsável, com cadastro, locações de múltiplos exemplares, devoluções parciais, pagamentos e bloqueios. O MVP começa pelo backend; regras detalhadas em ../requirements.md.

## Alternativas
- Monólito organizado somente por camadas globais: simples inicialmente, mas dispersa funcionalidades entre diretórios.
- Monólito modular: mantém uma implantação e organiza as responsabilidades por domínio.
- Microsserviços: acrescentam comunicação distribuída, operação e consistência entre serviços sem necessidade demonstrada.

## Decisão
- Uma aplicação Spring Boot, organizada por clientes, catálogo, locações e pagamentos.
- API REST com JSON.
- Banco relacional para relacionamentos, restrições e transações.
- Separação interna entre entrada HTTP, regras de negócio e acesso aos dados.
- Testes das regras de cobrança/bloqueio e testes de integração de banco e endpoints.
- Entregas incrementais: estrutura executável; clientes; catálogo/exemplares; locações; devoluções/pagamentos.

## Consequências
- Build e implantação únicos, com menor custo operacional inicial.
- Limites entre módulos devem ser preservados; organização em pacotes não garante isolamento automaticamente.
- Operações críticas exigem transações e proteção contra concorrência, especialmente retirada do mesmo exemplar e quitação duplicada.
- Escalabilidade inicial da aplicação como uma unidade. Separar serviços somente diante de necessidade comprovada.
- Versões, ferramentas, autenticação, contratos detalhados e modelagem física continuam pendentes de aprovação.
