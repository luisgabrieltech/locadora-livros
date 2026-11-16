# Arquitetura

## Direção aprovada
API REST/JSON em monólito modular Spring Boot com banco relacional. Decisão, alternativas e consequências em [ADR-001](decisions/001-arquitetura-inicial.md).

Módulos: clientes, catálogo (livros e exemplares), locações e pagamentos. Dentro de cada módulo, separar interface HTTP, regras e persistência conforme a necessidade, sem abstrações antecipadas.

## Stack aprovada
- Java 22 (JDK já instalado, por decisão do usuário) e Spring Boot estável compatível; confirmar patch no momento da geração.
- Maven com Wrapper para build reproduzível.
- Spring MVC, Bean Validation e Spring Data JPA.
- PostgreSQL e Flyway para persistência e migrações versionadas.
- Spring Security com HTTP Basic, um operador configurado externamente e acesso local; decisão em [ADR-003](decisions/003-seguranca-e-datas-locais.md).
- JUnit e Testcontainers para testes de integração com PostgreSQL. A execução desses testes depende de ambiente Docker compatível.

## Próximo alinhamento
Autenticação HTTP Basic e fuso America/Sao_Paulo aprovados. CORE-001 e CLI-001 validadas; catálogo e abertura de locações validados conforme roadmap. Antes de implementar cada entrega, especificar critérios, contratos e validação correspondentes.


## Ambiente e objetivo aprovados
Execução exclusivamente local para aprendizado e portfólio no GitHub, sem produção ou hospedagem no escopo atual. Manter qualidade de código, segurança proporcional, migrações, testes e CI. Planejar a execução reproduzível para quem clonar o repositório. Não introduzir infraestrutura de produção sem nova necessidade e alinhamento.


## Implementação inicial
Spring Boot 4.1.2, Maven 3.9.9 e PostgreSQL 17.7-alpine fixados. Migração V1 cria somente o schema locadora; tabelas de domínio serão criadas nas próximas entregas. Consulte README.md para configuração e docs/development.md para o estado das verificações.

## Catálogo
Livro e exemplar físico separados, com vínculo obrigatório e código único por cópia. Decisão em [ADR-004](decisions/004-modelo-catalogo.md); contratos em [catalogo-api.md](catalogo-api.md). Migrações atuais: V1 schema, V2 clientes, V3 livros e exemplares. Abertura, consulta, devoluções e quitação por item implementadas.

## Locações
Migração V4, transação integral, bloqueios pessimistas e índice único parcial protegem a retirada ativa. A disponibilidade é consultada em lote pela interface HistoricoExemplares. Política e consequências em [ADR-005](decisions/005-locacoes-e-concorrencia.md).

## Devoluções
Lotes transacionais por itens da locação, com ordem de bloqueios preservada e valores finais fixados usando tarifas históricas. Reutiliza V4, sem nova migração. Critérios e consequências em [ADR-006](decisions/006-devolucoes-atomicas.md). O registro financeiro permanece separado da quitação.

## Quitação
Pagamento é uma transição do item devolvido, sem novo cálculo de cobrança ou dependência bancária. Usa a data do servidor e o protocolo transacional existente. Encerramento da locação é derivado dos itens, sem flag duplicada. Decisão em [ADR-007](decisions/007-quitacao-por-item.md).

## Consultas operacionais
Filtros por situação/cliente e indicadores derivados na listagem. SQL agregada por locação com projeção, paginação e contagem no PostgreSQL, sem carregar coleções de itens para cada resumo. Contrato em [consultas-locacoes-api.md](consultas-locacoes-api.md) e alternativas/consequências no [ADR-008](decisions/008-consultas-operacionais.md).

## Interface Angular local
Workspace frontend/ com Angular 22, TypeScript estrito, componentes standalone e formulários reativos. Primeiro recorte: login e Clientes. Proxy local para a API, Basic/CSRF em memória e validação de negócio preservada no Spring. Decisão no [ADR-009](decisions/009-frontend-angular-local.md), execução e testes em [frontend.md](frontend.md). O JAR continua sendo apenas a API; não há SSR ou deploy no escopo.
