# Git, GitHub e integração contínua

## Estado e fluxo

Repositório local inicializado em main com um commit de base do MVP já construído e validado. Esse commit reúne o trabalho anterior; não representa uma reconstrução artificial de etapas antigas. A preparação do GitHub/CI fica na branch chore/github-ci, separada da base.

Adotar GitHub Flow: abrir uma branch curta a partir de main, implementar uma alteração coesa, validar, preparar PR e integrar após revisão e aprovação das verificações. Usar os prefixos e Conventional Commits definidos no AGENTS.md. Não editar o histórico publicado para simular entregas anteriores.

## Pipeline

O arquivo [.github/workflows/ci.yml](../.github/workflows/ci.yml) executa em push para main, pull requests destinados a main e acionamento manual. Há um job, Build e testes, com limite de 15 minutos:

1. Checkout com permissão de leitura e sem persistir credenciais no Git.
2. Temurin Java 22, mantendo a versão aprovada do projeto, e cache de dependências Maven.
3. Verificação do Docker do runner ubuntu-24.04.
4. Maven Wrapper 3.9.9: `sh ./mvnw --batch-mode --no-transfer-progress clean verify`.
5. Se houver falha, disponibilizar relatórios Surefire/Failsafe por sete dias, quando existirem.

Testcontainers cria seus próprios bancos PostgreSQL e credenciais sintéticas. Não configurar .env, secrets do projeto, banco Compose ou serviço PostgreSQL adicional na pipeline. Os testes falham se o Docker não estiver disponível. Não há publicação de pacote, imagem Docker ou deploy.

As três Actions oficiais são fixadas por SHA de commit, verificados nas tags públicas v7 (checkout), v6 (setup-java) e v7 (upload-artifact) em 16/11/2026. Ao atualizar, conferir o commit, o changelog e executar novamente a validação. Fontes: [checkout](https://github.com/actions/checkout), [setup-java](https://github.com/actions/setup-java), [upload-artifact](https://github.com/actions/upload-artifact) e [Maven no GitHub Actions](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven).

## Preparação e publicação

.gitignore exclui .env, diretórios da IDE, target e caches de build. .env.example contém apenas configuração sem senhas funcionais. Antes de cada commit, revisar `git status`, o diff e `git diff --cached --check`; não adicionar logs ou dados reais para demonstrar o projeto.

Próxima etapa externa, após definir e autorizar o repositório de destino:

1. Criar um repositório vazio no GitHub com nome e visibilidade definidos pelo usuário. Não gerar outro README no remoto, para evitar históricos independentes.
2. Configurar origin com a URL real. Não incluir tokens na URL nem nos arquivos.
3. Publicar main e a branch chore/github-ci e abrir PR para revisão da preparação de CI.
4. Conferir a execução real no GitHub, os 113 testes atuais e os relatórios se houver falha. Quantidades de testes podem evoluir; o check deve exigir sucesso dos testes, não um número fixo.
5. Após o primeiro check bem-sucedido, configurar proteção de main/ruleset para exigir PR e o check Build e testes, conforme os recursos disponíveis na conta. Essa configuração não é aplicada apenas por versionar o workflow.
6. Integrar após aprovação e acompanhar a execução em main.

Criar o workflow e validar localmente não comprova sucesso no runner GitHub. A primeira execução remota, configuração de proteção e eventual publicação permanecem pendentes até serem realizadas. Não adicionar badge com sucesso presumido.

Licença do código da aplicação ainda não definida; o usuário deve escolhê-la antes de anunciar condições de reutilização. Os avisos Apache dos scripts Maven Wrapper foram preservados.

## Validação local — 16/11/2026

- actionlint 1.7.12 executado sobre ci.yml sem erros; binário oficial em target/ci-tools, com SHA-256 conferido contra o arquivo de checksums da release. Não é dependência da aplicação.
- Sintaxe de mvnw validada com bash -n. git diff --check aprovado; .env, .idea, target e caches permanecem ignorados. Verificação limitada de padrões de chaves privadas/tokens no índice não encontrou ocorrências; isso não substitui revisão de dados antes da publicação.
- Base c39c186 extraída com git archive para target/ci-smoke, sem .env, IDE ou builds anteriores. Maven Wrapper executado nessa cópia com JDK 22: clean verify aprovado, 53 Surefire + 60 Failsafe = 113 testes, zero falhas/erros/ignorados. Seis pools Hikari encerrados e nenhum aviso de conexão fechada. Log: target/ci-clean-verify.log.
- A execução local usou modo offline e cache explícito .sandbox-m2/repository para respeitar as condições da sessão. O workflow usa resolução normal pela rede e runner Linux; download inicial das dependências, setup-java, cache e upload de relatórios no GitHub ainda não foram executados no serviço remoto.
- Commit inicial do MVP criado em main; configuração CI e documentação em commit separado na branch chore/github-ci. Não há origin, push, PR, merge ou proteção remota configurados nesta etapa.