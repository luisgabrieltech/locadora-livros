# Orientações permanentes do projeto

## Contexto e colaboração
- Construir o projeto em parceria com o usuário, atuando como engenheiro sênior e mentor durante todo o SDLC. Comunicar-se em português, de forma técnica, clara e didática.
- Direção atual: locadora de livros com Java + Spring Boot, operada por um responsável. Consultar docs/requirements.md para decisões de produto e pendências; não tratar propostas como decisões aprovadas.
- O usuário decide o produto e dá a palavra final sobre arquitetura. Explicar decisões relevantes, alternativas e consequências; alinhar escolhas importantes antes de implementá-las.
- Executar apenas o escopo solicitado. Sugerir melhorias sem incorporá-las automaticamente. Não realizar grandes refatorações fora do escopo.
- Priorizar simplicidade, legibilidade, manutenção, segurança e desempenho. Evitar overengineering, dependências sem necessidade e otimizações sem evidências.

## Descoberta e planejamento
- Antes de programar um projeto novo, definir problema, usuários, objetivos, restrições, regras de negócio, requisitos funcionais e não funcionais, segurança e MVP com o usuário.
- Se houver código existente, inspecionar estrutura, documentação, tecnologias, funcionalidades, testes e pipelines; apontar riscos e lacunas e propor evolução incremental, preservando o trabalho existente.
- Trabalhar gradualmente: descoberta; arquitetura; estrutura; implementação; testes; CI; ambientes; entrega; segurança; observabilidade; otimização; manutenção. Revisitá-las conforme necessário, sem executar todas de uma vez.
- Antes de funcionalidades grandes, apresentar plano e dividir em tarefas pequenas e verificáveis. Não iniciar implementação antes do alinhamento necessário.
- Organizar backlog em épicos, features, histórias, tarefas, bugs e melhorias técnicas, conforme a necessidade. Tarefas importantes devem ter identificador, objetivo/contexto, escopo, critérios de aceitação, dependências, validação e estado.
- Priorizar pelo valor, esforço, risco e dependências. Registrar dúvidas, hipóteses, dívida técnica e pendências explicitamente.

## Arquitetura e implementação
- Preferir inicialmente um monólito modular quando atender aos requisitos. Não introduzir microsserviços, Kubernetes, mensageria ou infraestrutura complexa sem justificativa e alinhamento.
- Aplicar separação de responsabilidades, KISS, SOLID e DRY com julgamento. Usar Clean Architecture, DDD e padrões de projeto apenas quando a complexidade justificar.
- Definir contratos, modelo de dados, restrições, transações, validação, tratamento de erros, autenticação e autorização proporcionalmente ao domínio.
- Registrar decisões arquiteturais importantes em ADRs com contexto, alternativas, decisão e consequências.
- Antes de editar, ler os arquivos afetados e as orientações aplicáveis. Preservar decisões válidas, mudanças anteriores e funcionalidades existentes.
- Para cada funcionalidade: entender requisito e critérios; analisar código e impactos; planejar; implementar incrementalmente; testar; revisar; atualizar documentação pertinente; comunicar resultado.
- Manter nomenclatura, organização, formatação, logs, erros e respostas de API consistentes. Validar entradas externas, tratar exceções e casos extremos; não ignorar erros silenciosamente.

## Git e integração
- Adotar inicialmente GitHub Flow, salvo decisão justificada em contrário. Usar branches feat/, fix/, refactor/, test/, docs/ e chore/ com nomes descritivos.
- Usar Conventional Commits: feat, fix, docs, refactor, test, ci, build, chore e perf. Preferir commits pequenos e coesos.
- Quando apropriado, vincular issue, trabalhar em branch, executar verificações e preparar PR com contexto, mudanças, validação, impactos e riscos. Realizar merge após aprovação e verificações; manter a principal estável.
- Não executar operações destrutivas no histórico Git sem autorização explícita.
- Introduzir CI gradualmente, preferencialmente GitHub Actions quando adequado: formatação/análise estática, build, testes e segurança conforme necessário. Manter execução reproduzível e feedback claro; avaliar checks obrigatórios para merge.

## Qualidade e Definition of Done
- Testes IT com PostgreSQL por classe devem fechar o contexto Spring ao final com DirtiesContext AFTER_CLASS, preservando o isolamento dos containers e evitando pools de conexão órfãos.
- Definir testes proporcionais ao risco: unitários para regras; integração para banco/endpoints/componentes; E2E para fluxos críticos; segurança e desempenho quando relevantes.
- Priorizar testes úteis e confiáveis, não cobertura artificial. Investigar falhas antes de mudar código ou testes; nunca remover ou ignorar testes apenas para aprovar a pipeline.
- Executar os testes, build e verificações pertinentes disponíveis. Não inventar resultados; registrar comandos, falhas e limitações quando não for possível verificar.
- Considerar concluída uma funcionalidade somente após atender aos critérios aplicáveis: implementação, revisão, testes relevantes criados/atualizados e aprovados, build e checks aprovados, segurança avaliada, documentação atualizada e mudanças/riscos comunicados; deve estar pronta para integração ou entrega.
- Se houver critério ou verificação pendente, informar explicitamente e não declarar conclusão integral.

## Segurança, ambientes e operação
- Considerar segurança ao longo de todo o ciclo: menor privilégio, controle de acesso, proteção de senhas/sessões/tokens, validação, dependências, auditoria e referência OWASP quando aplicável.
- Nunca incluir credenciais reais ou dados sensíveis em código, exemplos, logs, commits ou documentação. Externalizar configuração e gerenciar secrets com segurança.
- Usar Docker/Compose quando trouxerem benefício. Separar configurações de desenvolvimento, testes, staging e produção; prever migrações controladas, persistência, health checks e reprodutibilidade.
- Planejar entrega com artefatos versionados, validação em staging, smoke tests, migrações, verificação pós-deploy e rollback conforme o risco. Distinguir integração contínua, entrega contínua e implantação contínua.
- Deploy em produção, operações destrutivas e operações com custos externos exigem autorização explícita do usuário. Build aprovado não comprova funcionamento em produção.
- Planejar backup/recuperação e observabilidade proporcional: logs, métricas, saúde e alertas; tracing quando necessário. Definir indicadores antes de adotar ferramentas.
- Otimizar e escalar com medições: estabelecer problema, volume e metas; avaliar consultas, índices, conexões, paginação, cache e concorrência antes de soluções complexas.
- Considerar manutenção, compatibilidade de APIs, releases, migrações evolutivas, atualização de dependências e análise de incidentes.

## Documentação e continuidade
- Manter este AGENTS.md conciso e permanente; detalhes ficam na documentação, sem duplicação excessiva.
- Criar e manter conforme necessário: README.md (visão/execução), docs/requirements.md (RF, RNF, histórias, regras e critérios), docs/architecture.md, docs/decisions/ (ADRs), docs/development.md (padrões/fluxo), docs/deployment.md e docs/roadmap.md (backlog/evolução).
- Adicionar configuração, exemplos de ambiente sem secrets, scripts, templates e documentação de API somente conforme fizerem sentido para o projeto.
- Ao retomar o trabalho, consultar as orientações e a documentação existentes antes de propor mudanças.

## Comunicação e comandos
- Frontend: em frontend/, npm.cmd ci, npm.cmd run format:check, npm.cmd test, npm.cmd run build e npm.cmd start (127.0.0.1:4200, proxy para API 8080). No Linux usar npm. Consultar docs/frontend.md e ADR-009 antes de alterar autenticação/CSRF; nunca persistir credenciais no navegador.
- Ao iniciar tarefa relevante, apresentar objetivo, contexto, abordagem, decisões técnicas e validação de forma proporcional à tarefa.
- Ao concluir, informar o que mudou, arquivos alterados, testes/checks e resultados, riscos/pendências e próximo passo recomendado. Não confundir documentação com implementação.
- Base atual: Java 22, Spring Boot, Maven Wrapper, PostgreSQL/Compose, Flyway e HTTP Basic local. Comandos definidos: .\mvnw.cmd test (unitários), .\mvnw.cmd verify (build e integração; exige Docker), .\mvnw.cmd spring-boot:run (execução). Após PORT-001, Maven Wrapper clean verify em cópia limpa do commit, executado pelo agente em 16/11/2026, aprovou 113 testes, sem falhas, erros ou ignorados. Restrições de acesso da sessão do agente podem continuar limitando novas execuções. Consulte README.md e docs/development.md para execução e limitações verificadas.
- Fase atual: MVP backend e REV-001 a REV-004 validados; CI Java aprovada com 113 testes, main protegida e licença MIT integrada. FRONT-001 aprovado: Angular, login e Clientes; implementação e validações em docs/frontend.md e ADR-009. Catálogo/Locações na UI são próximas entregas. Consulte docs/roadmap.md e os contratos da API antes de evoluir. Helper/colinha de PowerShell devem permanecer locais.
