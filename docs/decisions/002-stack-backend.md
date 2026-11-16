# ADR-002 — Stack do backend

Estado: aprovado pelo usuário.

## Contexto
A arquitetura definida no ADR-001 exige API, validação, persistência relacional, segurança e testes reproduzíveis. Projeto novo, voltado a aprendizado e evolução profissional incremental.

## Alternativas
- Maven ou Gradle: ambos atendem; Maven com Wrapper oferece configuração declarativa e uma ferramenta de build reproduzível por projeto.
- JPA ou SQL explícito: adotar JPA para operações usuais; consultas específicas poderão ser avaliadas quando necessárias.
- Banco em memória ou PostgreSQL nos testes de integração: usar PostgreSQL para verificar o comportamento do banco escolhido.

## Decisão
Java 22, Spring Boot estável compatível, Maven com Wrapper, Spring MVC, Bean Validation, Spring Data JPA, PostgreSQL, Flyway, Spring Security, JUnit e Testcontainers.
Fixar versões exatas e conferir compatibilidade no momento de gerar o projeto.

## Consequências
- Usar o JDK 22 instalado para compilar e executar. A escolha anterior de Java 25 foi revista por solicitação explícita do usuário para aproveitar o ambiente existente.
- Maven global não é necessário após disponibilizar o Wrapper.
- Testcontainers requer Docker compatível. Não substituir silenciosamente esses testes por banco em memória.
- Migrações versionadas com Flyway; evitar alterações automáticas de schema em ambientes persistentes.
- O mecanismo de autenticação, inicialmente pendente, foi posteriormente aprovado como HTTP Basic local; consultar ADR-003.

## Revisão aprovada
Java alterado de 25 para 22 por solicitação do usuário. Compatibilidade verificada na documentação oficial: https://docs.spring.io/spring-boot/system-requirements.html . Confirmar dependências e executar build/testes na geração; compatibilidade declarada não substitui essa validação.
