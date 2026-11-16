# Roadmap

## Ordem de entregas aprovada
Estrutura executável → clientes → catálogo/exemplares → locações → devoluções/pagamentos.

## CORE-001 — Estrutura executável
- Estado: estrutura validada no terminal do usuário (BUILD SUCCESS informado). Relatórios locais conferidos em 16/11/2026: 11 testes aprovados, 0 falhas, 0 erros, 0 ignorados. Correção de CSRF validada. Não houve nova execução pelo agente nesta conferência.
- Objetivo/contexto: fornecer base reproduzível para implementar a API local de portfólio.
- Escopo: Java 22, Spring Boot com versão fixada, Maven Wrapper, configuração externa, PostgreSQL local via Compose, migração inicial necessária à base, Spring Security, README e teste de inicialização integrado ao banco.
- Dependências: stack e arquitetura aprovadas; Docker e JDK instalados; acesso ao Docker pela sessão do agente ainda limitado.
- Critérios: build pelo Wrapper; inicialização com PostgreSQL; migrações aplicadas; acesso protegido conforme ADR-003; nenhum secret real versionado; instruções reproduzíveis para clonar, configurar e testar.
- Validação: build/testes Maven, teste de integração com PostgreSQL por Testcontainers e verificação HTTP de autenticação. Registrar impedimentos sem declarar aprovação de checks não executados.
- Limite: não implementar cadastros nem regras de locação nesta entrega.

## Próximas tarefas — detalhar antes da implementação
- CLI-001: concluída e validada. Teste direcionado e verify aprovados pelo usuário; 35 testes aprovados nos relatórios locais (16/11/2026), incluindo CRUD, concorrência e CSRF reutilizável. Contrato e exemplos em [clientes-api.md](clientes-api.md).
- CAT-001: concluída e validada pelo usuário; 60 testes aprovados nos relatórios locais conferidos em 16/11/2026. Contrato em [catalogo-api.md](catalogo-api.md).
- LOC-001: abertura/consulta implementadas e validadas nesta sessão; 74 testes aprovados em verify (16/11/2026). Bloqueios, concorrência, disponibilidade e histórico cobertos. Fluxos de devolução/quitação serão validados por HTTP em DEV-001/PAG-001. Contrato em [locacoes-api.md](locacoes-api.md).
- DEV-001: concluída e validada nesta sessão, com 94 testes aprovados em verify (16/11/2026). Devolução parcial/integral do lote, cálculo final, dívida e concorrência cobertos. Contrato em [devolucoes-api.md](devolucoes-api.md). Pagamento ainda depende de PAG-001.
- PAG-001: concluída e validada nesta sessão: 106 testes aprovados em verify (16/11/2026). Quitação por item, lote integral, encerramento e liberação condicionada do cliente. Contrato em [pagamentos-api.md](pagamentos-api.md).

## Decisões aprovadas — ADR-003
- Autenticação HTTP Basic para um operador configurado externamente, sem cadastro público de usuários; limitar API e banco à interface de loopback no ambiente local.
- Fuso de negócio America/Sao_Paulo, configurável, para determinar a data atual.

## Próximos passos para alinhamento
- Revisar o checklist funcional do MVP, incluindo consultas operacionais de locações abertas/atrasadas e histórico, antes de declarar o produto integralmente concluído.
- Preparação local de Git/GitHub e CI em PORT-001 abaixo. Primeiro push, PR e execução remota permanecem pendentes.
- Ciclo de vida dos contextos Spring/containers corrigido e validado na REV-004. Preservar o encerramento por classe nas próximas integrações.
## Resultado da revisão do MVP (16/11/2026)
Relatório e critérios em [revisao-mvp.md](revisao-mvp.md). Situação após a revisão e a entrega de correções:
- REV-001 (P2): concluída; data/atraso revalidados após locks, com regressões de virada de dia.
- REV-002 (P2): concluída; prazos fora da faixa persistível retornam 400, com regressão HTTP e limite válido testados. Verify aprovado: 110 testes.
- REV-003 (P2): implementada e validada; filtros por situação/cliente e indicadores no resumo, paginação no banco sem duplicar locações. Contrato em [consultas-locacoes-api.md](consultas-locacoes-api.md), decisão no ADR-008. Verify aprovado: 113 testes.
- REV-004 (P3): concluída com DirtiesContext AFTER_CLASS nas seis classes de integração. Verify aprovado: 113 testes; seis pools encerrados e zero avisos de conexão fechada.
- Preparo do portfólio: base Git local criada e pipeline com Docker/JDK 22 preparada em PORT-001; publicação, execução remota, proteção de main e avaliação de padronização automatizada ainda pendentes.

## PORT-001 — Versionamento local e CI

- Objetivo/contexto: permitir revisão de mudanças e validação reproduzível do portfólio no GitHub.
- Escopo: repositório Git local em main, commit de base do MVP, branch chore/github-ci, workflow de build/testes e template de PR. Sem publicação ou deploy.
- Dependências: MVP e REV-001 a REV-004 validados; JDK 22, Maven Wrapper e Docker disponíveis.
- Critérios de aceitação: excluir .env/IDE/build/caches do versionamento; separar base e CI em commits; validar workflow; reproduzir clean verify com arquivos versionados sem .env; documentar limitações e pendências remotas.
- Validação: actionlint, verificação do índice/diff Git, sintaxe do Wrapper e clean verify em cópia limpa extraída do commit.
- Estado: preparação local concluída e validada. actionlint e Wrapper/sintaxe/diff aprovados; clean verify na cópia limpa aprovou 113 testes. Publicação, PR, execução GitHub e proteção de main continuam pendentes; detalhes em github.md.
