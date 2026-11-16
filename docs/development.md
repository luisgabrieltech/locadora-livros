# Desenvolvimento

## Diagnóstico inicial do ambiente
Verificado em 14/11/2026, por consulta ao PATH e execução dos comandos disponíveis:
- Java e javac: OpenJDK 22, em C:\Java\jdk-22\bin. Versão aprovada pelo usuário para o projeto; não é necessário instalar outro JDK.
- Git: 2.55.0.windows.5, disponível.
- Maven: não encontrado no PATH. O projeto usará Maven Wrapper, sem exigir Maven global.
- Docker: diagnóstico inicial sem comando no PATH, posteriormente atualizado conforme verificação abaixo.

Nenhuma instalação ou configuração de sistema foi realizada. Ausência no PATH não comprova ausência de instalação. PostgreSQL e outros diretórios de instalação ainda não foram inspecionados.

## Pendências antes da primeira entrega executável
- Usar JDK 22 e Docker Desktop já instalados. Verificar execução dos testes de integração quando o projeto existir; a sessão do agente encontrou restrição de acesso ao serviço Docker.
- Autenticação HTTP Basic e fuso America/Sao_Paulo aprovados; implementação inicial criada conforme ADR-003.
- Projeto gerado com versões fixadas, Wrapper e README. Validar build e execução.
- Registrar aqui os comandos reais de build e testes após sua criação e execução.

Há aplicação e testes na estrutura inicial. A definição dos comandos não representa validação de execução; resultados abaixo.

## Objetivo de entrega
Projeto de portfólio, executado apenas localmente. A documentação deverá permitir clonar, configurar, executar e testar usando dados fictícios. CI no GitHub faz parte da qualidade planejada; publicação do repositório não implica deploy da aplicação. Não planejar hospedagem, staging ou produção no escopo atual.


## Atualização — Docker instalado e motor ativo
O usuário forneceu a saída de docker version com Client e Server: Docker Engine 29.9.3, Docker Desktop 4.99.0, contexto desktop-linux, servidor linux/amd64. Docker Compose 5.7.0 foi identificado pela sessão do agente.
A sessão do agente encontrou acesso negado à configuração e ao named pipe do Docker. Motor ativo no terminal do usuário não comprova execução de Testcontainers pela sessão do agente. Nenhum container ou teste de integração do projeto foi executado até aqui.

## Verificação da entrega CORE-001
- POM: XML analisado com sucesso; Java 22 e Spring Boot 4.1.2 confirmados no arquivo.
- docker compose config --quiet: aprovado com senha fictícia somente no ambiente do processo de validação, sem iniciar containers. A ausência de POSTGRES_PASSWORD foi rejeitada como esperado. Docker ainda emitiu aviso de acesso negado ao arquivo de configuração pessoal da sessão.
- mvnw.cmd: porção PowerShell analisada sem erros de sintaxe; mvnw: bash -n aprovado.
- Comando tentado: .\mvnw.cmd -Dmaven.repo.local=C:\Users\corre\Desktop\FastGo\.sandbox-m2\repository verify, com MAVEN_USER_HOME apontando para .sandbox-m2 do workspace para respeitar limites de escrita da sessão.
- Resultado: falhou no download da distribuição Maven, antes de compilar ou executar testes, com impossibilidade de conexão ao servidor remoto.
- Não verificados: resolução das dependências, compilação, testes unitários, integração, migração executada no PostgreSQL e resposta HTTP em execução real. Os testes foram escritos, mas nenhum foi declarado aprovado.
- O usuário pode executar .\mvnw.cmd verify em seu PowerShell para obter a validação fora das restrições desta sessão. Não remover testes nem trocar PostgreSQL por banco em memória para contornar a limitação.

## Comandos definidos
- .\mvnw.cmd test: testes unitários pelo Surefire, sem necessidade de Docker.
- .\mvnw.cmd verify: empacotamento e testes de integração *IT pelo Failsafe, com Docker e PostgreSQL temporário do Testcontainers.
- .\mvnw.cmd spring-boot:run: executar a aplicação com .env e PostgreSQL do Compose.
- docker compose up -d --wait: iniciar PostgreSQL e aguardar saúde; requer .env preenchido.
- docker compose stop: parar o banco sem remover o volume.

Repositório Git inicializado em main em 16/11/2026, com commit inicial do MVP validado. Preparação de CI na branch chore/github-ci; nenhum remoto ou publicação no GitHub. Fluxo e próximos passos em [github.md](github.md).

## Ciclo de vida dos testes com PostgreSQL temporário

Cada classe de integração mantém seu próprio PostgreSQL estático via Testcontainers. Usar `@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)` nessas classes para retirar o contexto Spring do cache e fechá-lo ao término da classe, liberando o pool Hikari e o servidor HTTP associados ao banco temporário.

Não fechar o contexto após cada método: os testes da mesma classe compartilham a aplicação e limpam suas fixtures entre os casos. Também não reduzir maxLifetime, ocultar logs ou compartilhar o banco pessoal para contornar o encerramento de containers. Manter o ciclo de vida das extensões Testcontainers/Spring e validar a suíte completa ao alterar essa configuração.

Alternativa futura: containers geridos pelo contexto Spring. A solução atual preserva as dependências e o isolamento existentes com uma alteração pequena. Referência: [DirtiesContext no Spring](https://docs.spring.io/spring/reference/testing/annotations/integration-spring/annotation-dirtiescontext.html).

## Investigação — status CSRF substituído por 401
- Evidência do usuário: 5 testes ApplicationIT executados, 4 aprovados e 1 falha: POST sem token retornou 401 em vez de 403.
- Causa indicada pela configuração e comportamento do framework: a negação CSRF ocorre antes da autenticação Basic, e o tratamento do erro pelo servlet passa novamente pela autorização. A regra anyRequest exigia operador também no dispatcher ERROR, podendo mascarar o status original com 401.
- Correção: permitir somente dispatcher ERROR antes da regra de operador. Não liberar o caminho /error nem desabilitar CSRF. Referência: https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html#match-by-dispatcher-type .
- Mantido o teste original de 403, acrescido teste de acesso direto anônimo a /error. Criado SecurityHttpTest com servidor real e sem banco para testar o despacho de erros e a proteção dos caminhos normais; ApplicationIT permanece com PostgreSQL.
- Nesta sessão, executado Maven offline a partir da distribuição baixada pelo usuário, com repositório explícito C:\Users\corre\.m2\repository: os 3 OperatorPropertiesTest passaram antes da alteração; a tentativa de reproduzir ApplicationIT parou por acesso negado ao named pipe Docker.
- Após a correção, tentativa de mvn offline test foi impedida por AccessDeniedException ao ler JARs do cache Maven. Uma nova tentativa compilou as classes principais, mas falhou ao compilar os testes por acesso negado a jakarta.activation-api-2.1.4.jar. Novos testes e regressão completa ainda não validados; executar .\mvnw.cmd verify no terminal do usuário.
- Usuário informou PostgreSQL já instalado. README descreve uso opcional dessa instalação com banco dedicado; nenhuma conexão ou alteração nela foi realizada.
## CLI-001 — Implementação e limitações de validação (16/11/2026)
- Contrato aprovado antes da implementação. Criados módulo clientes, migração V2, endpoint CSRF e handler de erros MVC/domínio. Mantida autenticação HTTP Basic e proteção CSRF.
- CPF: normalização estrita e validação pelo Hibernate Validator; banco garante unicidade mesmo em concorrência. Tabela não contém flags de dívida/inativação antecipadas.
- Diagnósticos SQL do Hibernate podem incluir CPF no detalhe de violações. Categorias SqlExceptionHelper e org.hibernate.orm.jdbc.error desativadas; handler registra falhas inesperadas de integridade com mensagem sanitizada, sem SQL/valores. Não há logs de payload ou parâmetros.
- Testes preparados: normalização/validação, CRUD HTTP com PostgreSQL, parâmetros/erros, unicidade inclusive concorrente e direta no banco, paginação sem CPF e fluxo CSRF com cookie. Teste anterior de migração atualizado para V2; casos de CSRF existentes preservados.
- Tentativas reais: Maven offline com repositório explícito do usuário, nas fases compile e test. Compilação impedida por AccessDeniedException em JARs do cache (spring-boot-http-converter e jakarta.activation-api); tentativa com compilador externo também falhou. Não há execução bem-sucedida dos testes novos e não se declara build aprovado.
- Não reconfigurado o Maven do projeto para contornar a sessão e não desabilitado qualquer teste. Executar .\mvnw.cmd clean verify no terminal do usuário, com Docker ativo, para recompilar sem resíduos das tentativas incompletas e validar os testes.
- Não houve conexão nem migração no PostgreSQL pessoal do usuário. Os testes usam somente containers descartáveis.
## Correção — token CSRF invalidado entre chamadas (16/11/2026)
- Usuário executou verify: 14 testes de integração, 5 falhas de ClientesIT com HTTP 403 após chamadas anteriores. O padrão indicava invalidação do token, e não falha das validações de cliente.
- Diagnóstico executado: surefire:test de SecurityHttpTest sobre as classes compiladas pelo usuário, com TRACE de Spring Security, antes da correção. Os 3 testes existentes passaram; eles cobriam somente uma escrita válida por token.
- Evidência do rastreamento: SessionManagementFilter chamava ChangeSessionIdAuthenticationStrategy e CsrfAuthenticationStrategy; após a primeira escrita, aparecia Replaced CSRF Token. O fluxo Basic stateless estava sendo tratado como novas autenticações implícitas, invalidando o token mantido pelo cliente.
- Tentativa anterior: requireExplicitAuthenticationStrategy(true) junto de STATELESS. A execução seguinte do usuário demonstrou incompatibilidade nessa versão: Invalid configuration ... [sessionCreationPolicy = STATELESS]. Essa tentativa foi substituída pela configuração explícita abaixo.
- SecurityHttpTest ampliado para repetir a escrita com o mesmo token, intercalar GET autenticado e repetir a escrita novamente. Os testes de ClientesIT não foram relaxados ou removidos.
- Validação após alteração: tentativas de test e do teste direcionado impedidas na compilação por erro de acesso a JARs, inclusive com cópia do cache público Maven no workspace (.sandbox-m2, ignorado pelo Git). Nenhuma configuração global foi alterada. A etapa compile reportou sucesso após os arquivos principais serem gerados, mas testCompile continuou falhando; não há build completo nem regressão nova aprovados nesta sessão.
- Próxima verificação: .\mvnw.cmd clean verify no terminal do usuário, com Docker ativo. A entrega permanece pendente até essa execução passar.
## Correção do conflito de configuração da segurança (16/11/2026)
- Causa raiz lida no relatório SecurityHttpTest: a combinação de STATELESS com requireExplicitAuthenticationStrategy(true) impediu a criação de securityFilterChain. failure threshold era consequência desse erro inicial.
- Configuração atual: desabilitar somente SessionManagementConfigurer para não instalar o filtro de gerenciamento implícito de login; usar RequestAttributeSecurityContextRepository para autenticação por requisição e NullRequestCache para não armazenar requisições na sessão. CSRF continua ativo com HttpSessionCsrfTokenRepository, e cookie continua sem autenticar o usuário.
- Rastrear a sessão somente por cookie no servlet, sem identificador de sessão em URL. Mantidos endpoints protegidos, tratamento de ERROR e teste de reutilização do token em chamadas sucessivas.
- Tentado Maven offline com cache do workspace e -Dtest=SecurityHttpTest test. A tentativa foi impedida na compilação por erro de acesso ao JAR spring-boot-starter-tomcat-runtime. Não há teste aprovado desta revisão; relatórios anteriores não validam a configuração atual.
- Para feedback mais rápido no terminal do usuário, executar primeiro .\mvnw.cmd -Dtest=SecurityHttpTest test (sem Docker). Se passar, executar .\mvnw.cmd verify (com Docker) para a regressão completa.

## Validação final da CLI-001 (16/11/2026)
Usuário confirmou sucesso de .\mvnw.cmd -Dtest=SecurityHttpTest test e .\mvnw.cmd verify. Relatórios locais conferidos: ClienteRequestTest 15, OperatorPropertiesTest 3, SecurityHttpTest 3, ApplicationIT 6 e ClientesIT 8; total 35, sem falhas, erros ou testes ignorados. A correção de configuração e a reutilização do token CSRF estão validadas. Esta conferência leu os relatórios do usuário; não houve nova execução pelo agente. CLI-001 concluída; próximo passo é alinhar CAT-001 antes de implementar.

## CAT-001 — Catálogo (16/11/2026)
- Escopo aprovado antes da implementação: livros e exemplares físicos, com criar/consultar/listar/atualizar; sem exclusão nem locações.
- Criados módulo catalogo, migração V3 com FK e UNIQUE, validação ISBN-10/13, handlers 404/409 e testes unitários/HTTP com PostgreSQL real. Teste da migração da base espera agora V3. Nenhuma dependência nova ou alteração da autenticação/CSRF.
- Tentativa real: Maven offline com repositório .sandbox-m2/repository, fase test. Interrompida na compilação por acesso negado a spring-boot-starter-tomcat-runtime-4.1.2.jar, antes de executar os testes novos. Não declarar build ou testes da CAT-001 aprovados.
- Novos testes incluem código duplicado entre livros e sob concorrência, FK, rollback de atualização inválida, paginação e CSRF. Relatórios antigos de 35 testes pertencem à CLI-001 e não validam o catálogo.
- Próxima verificação: .\mvnw.cmd clean verify no terminal do usuário, com Docker ativo. Não houve conexão nem alteração no PostgreSQL pessoal do usuário.

## Validação da CAT-001 e planejamento LOC-001 (16/11/2026)
Usuário informou sucesso. Conferência dos relatórios locais: CatalogoRequestTest 17, ClienteRequestTest 15, OperatorPropertiesTest 3, SecurityHttpTest 3, ApplicationIT 6, CatalogoIT 8 e ClientesIT 8; total 60, sem falhas, erros ou ignorados. Conferência documental, sem nova execução pelo agente. CAT-001 validada. Próximo passo: alinhar contrato de locações em locacoes-api.md antes da implementação.

## LOC-001 — Implementação e validação (16/11/2026)
- Contrato aprovado antes da implementação. Criados locação/itens, V4, três endpoints, preços preservados e validações de abertura. Catálogo consulta disponibilidade em lote e protege troca de livro após histórico.
- Bloqueios: cliente primeiro e exemplares por UUID ordenado; índice parcial único impede retirada ativa duplicada. Fixtures de devolução/dívida/quitação verificam elegibilidade; seus fluxos HTTP permanecem em DEV-001/PAG-001.
- Verificação executada pelo agente nesta sessão com acesso autorizado ao cache e ao Docker, usando a distribuição Maven 3.9.9 do Wrapper: Maven offline, repositório explícito .sandbox-m2/repository e fase verify. BUILD SUCCESS, código 0.
- Surefire: 41 testes; Failsafe: 33 testes, incluindo 11 LocacoesIT com Clock fixo e PostgreSQL 17.7 real. Total 74, sem falhas, erros ou testes ignorados. Compilação, migrações V1–V4, empacotamento e regressão de clientes/catálogo/CSRF aprovados.
- Cenários adicionais: listas invertidas em requisições concorrentes, ausência de gravação parcial, dados/preços desconhecidos rejeitados, cópia liberada após estado de devolução e bloqueio do cliente até quitação. Não houve conexão ou alteração no PostgreSQL pessoal do usuário.
- O teste completo pode ser repetido normalmente pelo usuário com .\mvnw.cmd verify. Não há necessidade de configurar o cache especial .sandbox-m2 fora da sessão do agente.
- Próxima entrega: alinhar DEV-001 (devolução parcial, cálculo final e estado financeiro), preservando a ordem de locks do ADR-005. Não existe endpoint de devolução/pagamento ainda; retiradas abertas permanecem ativas nessa versão.
## DEV-001 — Implementação e validação (16/11/2026)
- Implementado POST /api/v1/locacoes/{locacaoId}/devolucoes: lista de itens, data do Clock, lote integral, bloqueio de cliente/exemplares e rejeição de repetição. Projeção inicial de clienteId evita carregar itens desatualizados antes de aguardar o lock.
- Cálculo BigDecimal com tarifas preservadas, mínimo de uma diária, multa por atraso e limite NUMERIC(12,2), sem arredondamento silencioso. Data e valor final persistidos nos campos da V4; nenhuma nova migração ou edição de migração aplicada.
- GET de locação inclui dataDevolucao, valorTotal e dataPagamento. Não há endpoint de quitação; dívida mantém o cliente bloqueado e não mantém a cópia indisponível.
- Testes anteriores de dívida/histórico passaram a registrar devolução pela API. Fixture de pagamento permanece apenas até PAG-001.
- Primeiro verify detectou uma asserção que comparava representação textual JSON de 2.00 com 2.0. Corrigida para comparação monetária BigDecimal, mantendo a exigência do valor exato. Nenhuma regra ou expectativa de negócio foi relaxada.
- Verify final executado pelo agente com Maven 3.9.9 offline, repositório explícito .sandbox-m2/repository e acesso autorizado ao Docker: BUILD SUCCESS, código 0. Surefire 50, Failsafe 44; total 94, sem falhas, erros ou ignorados.
- Inclui 9 CalculoDevolucaoTest e 11 DevolucoesIT, com Clock controlado/PostgreSQL real: devolução parcial/antecipada/atrasada, limite monetário, lote inválido sem alteração parcial, concorrência sobreposta e reempréstimo protegido contra devolução antiga repetida. Regressão de clientes, catálogo, locações e CSRF aprovada.
- Nenhum dado do PostgreSQL pessoal foi acessado. Próximo passo: alinhar PAG-001, mantendo o protocolo de locks dos ADRs 005/006 e a cobrança já fixada por item.
## PAG-001 — Implementação e validação (16/11/2026)
- Criado POST /api/v1/locacoes/{locacaoId}/pagamentos com lista de itens devolvidos e não quitados. Data e valor vêm do servidor; nenhuma alteração do total fixado na devolução. Lote integral e conflitos para pagamento repetido/antecipado, com locks e leitura atualizada após espera.
- GET detalhado inclui encerrada, derivado de todos os itens devolvidos e pagos. Listagens preservam o resumo anterior. Nenhum flag financeiro duplicado ou migração nova.
- Substituída a fixture de pagamento em LocacoesIT pelo fluxo HTTP real. PagamentosIT reutiliza o Clock controlável da infraestrutura de DevolucoesIT, com contexto e banco próprios.
- Verify executado pelo agente com Maven 3.9.9 offline e cache explícito .sandbox-m2/repository, com acesso autorizado ao Docker. BUILD SUCCESS, código 0: Surefire 50 e Failsafe 56; total 106 testes, sem falhas, erros ou ignorados.
- Doze PagamentosIT cobrem quitação dias após a devolução sem acréscimo, dívida em outra locação, atraso ainda ativo, lotes inválidos sem quitação parcial, repetição/concorrência, retirada concorrente e preservação da disponibilidade de cópia retirada por outro cliente. Basic e CSRF preservados.
- Não houve conexão com o PostgreSQL pessoal nem envio de dados ao GitHub ou banco externo. Não há gateway, estorno, parcelamento ou alteração de quitação.
- Dívida técnica observada: pools de contextos Spring mantidos no cache de testes tentam validar conexões de containers de classes anteriores já encerrados, gerando avisos Hikari. Não causou falhas nesta execução. Revisar descarte dos contextos/lifecycle dos containers na etapa de QA/CI, sem mascarar com maxLifetime da aplicação.
- Próximo passo: revisar checklist do MVP e alinhar preparo do repositório/CI para o portfólio.
## Revisão do MVP (16/11/2026)
Nova execução de verify aprovada (106 testes, 0 falhas/erros/ignorados). Sondas isoladas sob target/review reproduziram captura de data anterior após espera simulada por lock e erro HTTP 500 para prazo +10000000-01-01, com rollback. Relatório em revisao-mvp.md; código funcional e testes versionados não foram alterados. Consultas de abertas/atrasadas, lifecycle de pools e Git/CI continuam pendentes.
## Correções REV-001/002 (16/11/2026)
Data efetiva de abertura após os locks e validação da faixa persistível do prazo. Regressões em LocacaoServiceTest e LocacoesIT. Maven verify offline, cache .sandbox-m2/repository, JDK 22 e Docker: BUILD SUCCESS, 53 testes Surefire e 57 Failsafe (110 no total), zero falhas/erros/ignorados. Log em target/review-fixes-verify.log. REV-003/004 permanecem abertas; avisos Hikari de lifecycle dos containers ainda presentes. Detalhes em revisao-mvp.md.

## REV-003 — Consultas operacionais (16/11/2026)
Filtros de situação/cliente e indicadores no resumo, sem nova migração. Maven verify offline com cache .sandbox-m2/repository, JDK 22 e Docker aprovado: 53 Surefire + 60 Failsafe = 113 testes, zero falhas/erros/ignorados (target/consultas-verify.log). Regressão ampliada de devolução parcial validada novamente por -Dtest=PagamentosIT test: 13 aprovados, sem aumentar o total distinto da suíte (target/consultas-parcial-test.log). Contrato/limites em consultas-locacoes-api.md e ADR-008. Avisos Hikari da REV-004 continuam presentes na suíte completa.

## REV-004 — Validação de lifecycle (16/11/2026)
DirtiesContext AFTER_CLASS nas seis classes IT com PostgreSQL. Maven verify offline com JDK 22 e Docker aprovado: 53 Surefire + 60 Failsafe = 113 testes, zero falhas/erros/ignorados. Log target/lifecycle-verify.log: seis pools Hikari encerrados ao final das respectivas classes e nenhum aviso de conexão fechada (antes: 11 em target/consultas-verify.log). Produção, maxLifetime e logs preservados; PostgreSQL pessoal não utilizado. Pendência resolvida; próximo passo é preparar Git/CI para o portfólio.

## PORT-001 — Git local e CI (16/11/2026)
Git inicializado em main e commit de base c39c186 criado com a identidade já configurada. Branch chore/github-ci para workflow GitHub Actions e documentação, sem remoto/push/merge. actionlint 1.7.12 e bash -n mvnw aprovados. Cópia limpa de git archive, sem .env, validada pelo Maven Wrapper clean verify offline com cache explícito: 53 Surefire + 60 Failsafe = 113 testes, zero falhas/erros/ignorados, seis pools encerrados e zero avisos de conexão fechada. Log target/ci-clean-verify.log. Execução no runner GitHub e proteção de main não verificadas; consulte github.md.

## FRONT-001 — Login e Clientes no Angular (16/11/2026)
Workspace frontend/ com Angular CLI 22.2.7 e Node 24.19.0, mantendo a API e seus contratos. Formatação Prettier, 19 testes Vitest com relatório JUnit e build aprovados localmente. Teste real em Edge headless cobriu login inválido/válido, CPF inválido, criação, duplicidade, edição, email removido, logout e reload; um cliente fictício foi criado, sem alterar registros existentes. Inspeção desktop/mobile e Axe sem violações nos seis estados/telas verificados. Runtime npm audit reportou zero vulnerabilidades; dependências dev/backend não incluídas nessa análise. Artefatos de QA em target ignorado. Frontend deixou de depender do PowerShell para operar Clientes; helper/colinha anteriores mantidos locais. CI mantém o check Build e testes incluindo frontend e verify Java. Documentação/limites em frontend.md e ADR-009; não confundir conclusão de FRONT-001 com as futuras telas Catálogo/Locações.
