# Revisão técnica e checklist do MVP — 16/11/2026

Escopo: código atual, regras aprovadas, segurança local, transações/concorrência, persistência, testes e preparação do portfólio. Revisão sem alterar código de produção ou testes versionados.

## Achados em ordem de prioridade

### REV-001 — P2: data da abertura capturada antes de aguardar locks

Estado: corrigido e validado em 16/11/2026; detalhes abaixo. Referência original: LocacaoService.java, linhas 39–42 e 51.

O serviço captura hoje e valida prazos antes de adquirir o lock do cliente e dos exemplares. Se a espera atravessar a meia-noite no fuso de negócio, a consulta de elegibilidade e a data gravada continuam usando o dia anterior. Um cliente cujo exemplar acabou de ficar atrasado pode ser liberado com base nessa data antiga.

Evidência: sonda unitária isolada em target/review/ReviewProbe.java, usando o serviço real e repositórios simulados, avançou o Clock durante a obtenção do lock. Resultado: data atual 17/11, elegibilidade consultada em 16/11, retirada marcada em 16/11 e persistência invocada. Não foi uma gravação real de locação nessa sonda.

Recomendação: definir o instante de negócio após adquirir os locks necessários, revalidando prazo e elegibilidade com uma única data coerente. Não alterar essa regra apenas no teste. Dependência: preservar a ordem cliente → exemplares por UUID do ADR-005.

Aceitação/validação: regressão com Clock controlado avançando durante a espera dos locks; o cliente agora atrasado deve ser bloqueado e nenhuma locação gravada. Cobrir também prazo que se tornou anterior à data efetiva da operação.

### REV-002 — P2: prazo aceito pelo Java excede o tipo DATE do banco

Estado: corrigido e validado em 16/11/2026; detalhes abaixo. Referências originais: LocacaoRequest.java, linha 22; RegrasAbertura.java, linha 22; V4__create_locacoes.sql, linha 12.

A validação exige data presente e não anterior à retirada, mas não limita a faixa persistível. LocalDate aceita anos além do limite do PostgreSQL. Uma entrada inválida para o armazenamento acaba tratada como erro interno de integridade.

Evidência HTTP com API real, credenciais/token sintéticos e PostgreSQL descartável: dataPrevistaDevolucao=+10000000-01-01 retornou 500; a transação foi desfeita e nenhuma locação foi persistida. A sonda e seu log estão em target/review, fora do código versionado.

Recomendação: rejeitar a data fora da faixa suportada com 400 antes da persistência. Uma restrição de negócio mais curta, como prazo máximo de locação, exige alinhamento separado; não inventar esse limite na correção.

Aceitação/validação: entrada extrema retorna application/problem+json com 400 e zero gravações; testar limites válidos sem trocar por uma restrição arbitrária de dias. Referência técnica: [faixa de DATE no PostgreSQL 17](https://www.postgresql.org/docs/17/datatype-datetime.html).

### REV-003 — P2: consulta operacional de abertas/atrasadas incompleta

Estado: corrigido e validado em 16/11/2026; contrato em consultas-locacoes-api.md e decisão no ADR-008. Descrição abaixo preserva o achado original. Referências: requirements.md, item de consultas do MVP; LocacaoController.java, linhas 35–38; LocacaoService.java, linhas 66–71; LocacaoResponse.Resumo.

O GET de listagem retorna todas as locações, paginadas, com id/clienteId/dataRetirada. Não recebe filtro nem permite identificar abertas ou atrasadas diretamente no resumo. O operador precisaria consultar cada detalhe para classificar manualmente as locações.

Os cadastros, a disponibilidade das cópias e o histórico detalhado persistido existem, mas não considerar integralmente atendido o requisito de consulta de locações abertas e atrasadas.

Recomendação: alinhar filtros/contrato operacional. Distinguir pendência de devolução de pendência financeira: uma locação com tudo devolvido e dívida não é uma locação com exemplar atrasado.

Aceitação/validação: consultar abertas/atrasadas com paginação correta, inclusive devoluções parciais; no vencimento não há atraso, no dia seguinte há; cópia devolvida com dívida não deve aparecer como exemplar ainda atrasado. Dependência: alinhar o contrato antes de implementar.

### REV-004 — P3: pools de testes sobrevivem aos containers de suas classes

Estado: corrigido e validado em 16/11/2026; detalhes da correção abaixo. A descrição seguinte preserva o achado original. Referências: ApplicationIT.java, linhas 25–36, e demais ITs com @Container estático e contexto Spring em cache.

Na execução completa aparecem avisos Hikari de conexões fechadas em pools de classes anteriores, após o respectivo PostgreSQL temporário encerrar. O contexto/pool permanece vivo enquanto a suíte avança. A revisão reproduziu os avisos; não houve falha de teste por esse motivo nesta execução.

Recomendação: alinhar o descarte do contexto com o ciclo de vida do container, ou gerir o container pelo contexto. Não diminuir maxLifetime da aplicação para mascarar encerramento deliberado do banco do teste.

Aceitação/validação: verify completo, preservando isolamento entre bancos e sem os avisos de pools referenciando containers encerrados. Dependência: nenhuma alteração da regra de negócio.

## Checklist funcional

| Área | Resultado da revisão |
|---|---|
| Clientes, CPF único/validado e contato | Implementado; regressão aprovada |
| Livros e exemplares identificados | Implementado; regressão aprovada |
| Retirada de vários exemplares, atomicidade e exclusividade | Implementado; REV-001/002 corrigidas e validadas |
| Disponibilidade e proteção do histórico | Implementado; regressão aprovada |
| Devolução parcial, mínimo de diária e multa por atraso | Implementado; regressão aprovada |
| Valor fixado na devolução e quitação integral por item | Implementado; regressão aprovada |
| Bloqueio por dívida/atraso e liberação após regularização | Implementado; regressão de virada de data aprovada em REV-001 |
| Histórico detalhado de locações | Persistido e consultável por ID |
| Consulta operacional de abertas/atrasadas | Implementado; REV-003 validada |

## Preparação do GitHub — ainda pendente

- Não há .git nesta pasta nem workflow em .github/workflows. Ainda faltam versionamento local, remoto e CI; não foi feita consulta a contas/repositórios externos nesta revisão.
- Preparar GitHub Actions para build/testes com JDK 22 e Docker; validar execução de integração e publicar relatórios de falha. Publicar repositório/push depende do escopo que for solicitado depois.
- README, exemplos, ADRs, Wrapper, .gitignore e configuração externa existem. .env.example não contém senhas funcionais. Não foi aberto arquivo pessoal de credenciais.
- Não há formatter/análise estática configurados no build; avaliar uma ferramenta simples na etapa de padronização, sem multiplicar ferramentas redundantes.

## Verificações e limites

- Maven verify executado novamente pelo agente: BUILD SUCCESS; 50 testes Surefire e 56 Failsafe, total 106, sem falhas, erros ou ignorados. PostgreSQL 17.7/Testcontainers, sem acesso ao banco pessoal.
- As sondas da revisão não fazem parte desses 106 testes: virada de data simulada com o serviço real e data extrema por HTTP contra PostgreSQL real; resultados descritos acima.
- Código de produção e testes versionados não foram corrigidos nesta revisão. Criados apenas relatório/documentação e artefatos de diagnóstico em target, já ignorado pelo Git.
- Não foram executados análise de vulnerabilidades de dependências, pentest, carga ou benchmarks. A revisão não certifica ausência de vulnerabilidades. Configuração local Basic/CSRF e restrição a loopback foram inspecionadas; não há escopo de produção.

Ordem recomendada: REV-001/002 → alinhar/implementar REV-003 → REV-004 e preparo de Git/CI → conferência final do MVP e apresentação no GitHub.

## Correções REV-001 e REV-002 — 16/11/2026
- Data efetiva capturada após todos os locks, seguida de revalidação dos prazos e consulta de elegibilidade. Preservada a ordem global de bloqueios e a validação inicial de entrada.
- Rejeição com 400 de prazo além de 31/12/5874897, limite técnico do PostgreSQL, sem limite comercial adicional e sem migração.
- LocacaoServiceTest: três regressões de virada de dia durante locks do cliente/exemplar, bloqueio por atraso, prazo vencido e data de retirada coerente.
- LocacoesIT: rejeição HTTP de dois prazos fora da faixa sem persistência, disponibilidade preservada e aceitação da última data suportada pelo banco real.
- Maven verify offline com JDK 22 e PostgreSQL descartável via Docker: BUILD SUCCESS; 53 testes unitários/HTTP e 57 de integração, total 110, zero falhas/erros/ignorados. Log: target/review-fixes-verify.log. Banco pessoal não utilizado.
- Pendentes: REV-003, REV-004 e preparo Git/CI. Os avisos Hikari da REV-004 continuam presentes; não houve falha de teste por esse motivo.

## REV-003 — Consultas operacionais (16/11/2026)
Contrato autorizado pelo usuário e implementado com filtros opcionais por situação/cliente e indicadores derivados. SQL agregada por locação com paginação/contagem no banco, sem novas tabelas, sem alterar gravações e sem expor dados pessoais. Maven verify aprovado: 53 Surefire + 60 Failsafe = 113 testes, zero falhas/erros/ignorados. Log em target/consultas-verify.log. REV-004 e Git/CI continuam pendentes.

## REV-004 — Ciclo de vida de testes (16/11/2026)

- Adicionado @DirtiesContext(classMode = AFTER_CLASS) a ApplicationIT, CatalogoIT, ClientesIT, LocacoesIT, DevolucoesIT e PagamentosIT. O contexto Spring, JPA, pool Hikari e servidor HTTP são fechados ao final de cada classe; os métodos da mesma classe continuam compartilhando o contexto.
- Preservados os bancos descartáveis independentes por classe e a limpeza de fixtures. Nenhuma mudança em código de produção, dependências, parâmetros Hikari ou níveis de log.
- Maven verify offline com JDK 22, cache .sandbox-m2/repository e Docker/PostgreSQL temporário: BUILD SUCCESS, 53 Surefire + 60 Failsafe = 113 testes, zero falhas/erros/ignorados. Log: target/lifecycle-verify.log.
- Confirmados seis pools Hikari encerrados na thread principal ao término das respectivas classes. Nenhum aviso de validação de conexão fechada; a execução anterior target/consultas-verify.log continha 11 desses avisos. Não houve ocultação de logs.
- REV-001 a REV-004 corrigidas e validadas. Ainda falta preparar Git/remoto, CI e apresentação do portfólio; esses passos não foram executados nesta correção.
## Preparação local do portfólio — PORT-001 (16/11/2026)
Git local e commit inicial criados; CI com Java 22/Wrapper/Docker, template de PR e guia de publicação preparados na branch chore/github-ci. Workflow validado com actionlint; clean verify do Wrapper em cópia limpa do commit aprovou 113 testes, sem .env nem avisos de conexão fechada. GitHub remoto, primeiro push/PR, execução real de Actions e proteção de main continuam pendentes. Não foi configurada análise estática de Java ou licença da aplicação; avaliar essas escolhas separadamente, sem afirmar que esta pipeline as executa. Detalhes em github.md.
