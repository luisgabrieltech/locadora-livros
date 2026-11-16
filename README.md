# Locadora de livros — API

Projeto de portfólio com Java 22 e Spring Boot, executado localmente. O objetivo é desenvolver uma API de locação de livros com regras verificáveis, entregas incrementais e histórico técnico compreensível.

## Estado atual

Implementados e validados: clientes, livros, exemplares, abertura/consulta de locações, devoluções parciais e quitação por item, com autenticação HTTP Basic, CSRF, migrações e PostgreSQL local. A última execução de Maven verify nesta sessão aprovou 113 testes em 16/11/2026, sem falhas, erros ou ignorados.

O responsável pode registrar a quitação integral de um ou mais itens devolvidos, inclusive imediatamente após a devolução, sem integração bancária. A devolução encerra a cobrança do item e libera a cópia; o valor fica pendente até a quitação. Todos os exemplares devolvidos não significam locação financeiramente quitada. Consulte [requisitos](docs/requirements.md), [arquitetura](docs/architecture.md) e [roadmap](docs/roadmap.md).

## API de clientes

Contrato, validações, códigos de erro e exemplo completo no PowerShell em [docs/clientes-api.md](docs/clientes-api.md). Novos testes incluem concorrência de CPF, paginação sem CPF e segurança das operações de escrita.

## Catálogo de livros e exemplares

Cadastros, consultas e atualização de títulos/cópias, com código único e ISBN opcional validado. Consulte [contrato, limites e exemplos](docs/catalogo-api.md). A disponibilidade é derivada das retiradas ativas. Cópias com histórico não podem ser transferidas para outro livro.

## Locações

Abertura de múltiplos exemplares, consulta e paginação. Retirada na data atual, prazo por cópia, tarifas preservadas e bloqueios por indisponibilidade/atraso/dívida. Operação integral e proteção contra retiradas simultâneas. Veja [contrato e exemplo](docs/locacoes-api.md). A listagem permite filtrar abertas, atrasadas, pagamentos pendentes e encerradas, além do histórico por cliente; consulte [consultas operacionais](docs/consultas-locacoes-api.md).

## Devoluções

Devolução parcial ou total, cálculo das diárias/multas com tarifas preservadas e valor fixado por item. Repetições são rejeitadas sem alterar o histórico. Veja [contrato e exemplo](docs/devolucoes-api.md).

## Pagamentos

Quitação integral por item devolvido, com valor preservado, proteção contra repetição e atualização do encerramento da locação. Veja [contrato e exemplo](docs/pagamentos-api.md). Não há pagamento parcial de item, gateway ou estorno.

## Tecnologias

Java 22, Spring Boot 4.1.2, Maven 3.9.9 via Wrapper, Spring MVC, Bean Validation, Spring Security, Spring Data JPA, PostgreSQL 17.7, Flyway e JUnit/Testcontainers. As versões das dependências são gerenciadas pelo Spring Boot.

## Requisitos locais

- JDK 22 e JAVA_HOME apontando para sua instalação.
- Docker Desktop em execução, no modo de containers Linux, com Docker Compose.
- Internet no primeiro build e no primeiro download das imagens.
- Portas locais 8080 (API) e 5432 (banco) disponíveis. O banco permite alterar DB_PORT.

Não é necessário instalar Maven globalmente.

## Executar no PowerShell

Clone o repositório e entre na pasta:

```powershell
git clone https://github.com/luisgabrieltech/locadora-livros.git
cd locadora-livros
```

Abra o terminal na raiz do projeto. Crie o arquivo de configuração:

```powershell
Copy-Item .env.example .env
notepad .env
```

Preencha POSTGRES_PASSWORD e APP_PASSWORD com senhas locais diferentes e não vazias. Use valores alfanuméricos sem aspas para manter o mesmo significado no Compose e na importação de propriedades do Spring. O arquivo .env não é versionado e não é um script.

Inicie o banco e aguarde o health check:

```powershell
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

A API fica em http://127.0.0.1:8080. O Compose lê .env para o banco e o Spring importa o mesmo arquivo para a aplicação; variáveis de ambiente podem sobrescrever a configuração.

Em outro terminal, consulte o status usando o usuário e a senha definidos no arquivo:

```powershell
curl.exe --user operador http://127.0.0.1:8080/api/v1/status
```

O curl solicita a senha, evitando incluí-la diretamente no comando. Se alterar APP_USERNAME, use o novo nome. Resposta esperada com credenciais válidas:

```json
{"application":"locadora-livros","status":"UP","timeZone":"America/Sao_Paulo"}
```

Sem credenciais ou com senha incorreta, a resposta esperada é HTTP 401. O endpoint indica que a aplicação atende requisições; não é um diagnóstico contínuo de saúde do banco.

API e porta publicada do banco ficam vinculadas a 127.0.0.1. Este arranjo HTTP Basic sobre HTTP é destinado exclusivamente ao uso local, sem exposição na rede.

## Testes e build

```powershell
# Testes de configuração e segurança HTTP; não exige Docker
.\mvnw.cmd test

# Build completo, incluindo integração com PostgreSQL temporário
.\mvnw.cmd verify
```

No Linux/macOS, use sh ./mvnw no lugar de .\mvnw.cmd.

O verify inicia seu próprio PostgreSQL via Testcontainers: não usa nem altera o banco do Compose. Os testes de integração falham se Docker estiver indisponível; não há exclusão automática desses testes.

O pacote é gerado em target/locadora-livros-0.1.0-SNAPSHOT.jar. Para executá-lo, a partir da raiz com .env configurado e banco ativo:

```powershell
java -jar target/locadora-livros-0.1.0-SNAPSHOT.jar
```

## Integração contínua e colaboração

A [pipeline de CI](.github/workflows/ci.yml) está preparada para pull requests destinados a main, pushes em main e execução manual. Usa Java 22, Maven Wrapper e Docker para rodar clean verify. Em caso de falha, disponibiliza os relatórios dos testes por sete dias. Nenhum secret do projeto ou .env é necessário para os testes.

As branches foram publicadas e a integração da pipeline está no [PR #1](https://github.com/luisgabrieltech/locadora-livros/pull/1). Confira o resultado correspondente ao último commit na aba Checks do PR ou em [Actions](https://github.com/luisgabrieltech/locadora-livros/actions). A proteção de main ainda depende de configuração no GitHub. Consulte [fluxo Git/GitHub e checklist de publicação](docs/github.md). Há um template de Pull Request com objetivo, alterações, validação e riscos.

## Usar PostgreSQL já instalado

O banco do Compose é uma conveniência. Para usar sua instalação local, configure em .env POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD e DB_PORT conforme um banco dedicado ao projeto, com usuário autorizado a criar o schema locadora. Crie esse banco antes de iniciar a API; o Flyway cria o schema e controla as migrações. Não use um banco com dados de outros projetos.

Nesse caso, não execute docker compose up nem docker compose stop. Inicie somente a API com .\mvnw.cmd spring-boot:run. A aplicação conecta-se a 127.0.0.1 na porta configurada. Evite iniciar o container na mesma porta da instalação existente; caso prefira Compose, escolha outra DB_PORT livre.

Os testes de integração continuam usando PostgreSQL temporário do Testcontainers e não utilizam sua instalação local. Não é necessário fornecer credenciais desse banco nos testes.

## Encerrar

Interrompa a API com Ctrl+C e pare o banco:

```powershell
docker compose stop
```

Os dados permanecem no volume. Alterar POSTGRES_PASSWORD no arquivo não troca a senha de um banco já inicializado; não remova o volume para resolver isso sem avaliar os dados.

## Segurança e evolução

- Um operador configurado externamente; senha codificada para autenticação em memória durante a execução.
- Nenhuma credencial padrão funcional ou cadastro público de usuários.
- Proteção CSRF mantida: obtenha token e cookie em GET /api/v1/csrf e envie ambos com as credenciais em POST/PUT. Consulte [contrato e exemplos de clientes](docs/clientes-api.md).
- Use dados fictícios nas demonstrações. Não versione .env, dados reais ou logs com credenciais/CPF.
- Reservas, renovação de prazo e tratamento de perda/dano estão fora do MVP.

## Licença

O código original da aplicação está disponível sob a [licença MIT](LICENSE). Os componentes de terceiros mantêm suas próprias licenças.

Os scripts Maven Wrapper vêm do Apache Maven Wrapper 3.3.4, sob Apache License 2.0, com os avisos preservados. Origem: https://github.com/apache/maven-wrapper/tree/maven-wrapper-3.3.4/maven-wrapper-distribution/src/resources .
