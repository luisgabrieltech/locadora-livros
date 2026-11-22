# ADR-009 — Interface Angular local

Estado: aprovado pelo usuário em 16/11/2026: estrutura Angular, login e Clientes como primeira entrega.

## Contexto e alternativas

O backend do MVP está implementado e validado. O usuário quer operar a locadora pelo navegador e escolheu Angular, mantendo execução local. A proposta completa inclui Catálogo e Locações em entregas posteriores.

Uma interface somente HTML simplificaria dependências, mas não atende à stack escolhida. SSR e infraestrutura de publicação não agregam valor ao painel local. Reescrever autenticação ou regras de negócio aumentaria o risco sem necessidade.

## Decisão

- Workspace frontend/ no mesmo repositório, com Angular CLI 22.2.7, componentes standalone, TypeScript estrito, formulários reativos, Router e HttpClient. Reutilizar Node 24.19.0 instalado, compatível com Angular 22; dependências fixadas por package-lock.json.
- CSS próprio e assets SVG/CSS locais; sem biblioteca de componentes, gerenciador global de estado, fontes remotas ou SSR.
- Login obtém CSRF pela API com Basic. Credenciais e token apenas em memória, sem armazenamento no navegador; um reload exige novo login. Router guard melhora navegação; autorização permanece na API.
- Interceptor funcional envia Basic apenas para caminhos relativos /api/v1/; adiciona o header CSRF informado pelo servidor nas escritas. Cookie permanece sob controle do navegador. Não repetir POST/PUT automaticamente.
- Proxy local de /api para 127.0.0.1:8080 preserva o fluxo atual sem alterar CORS, autenticação ou configuração de segurança do Spring. X-Requested-With evita que a falha Basic seja apresentada como diálogo nativo do navegador.
- Campos e operações limitados aos contratos atuais de Clientes. Não exibir CPF na tabela; buscar detalhe somente para editar. Não adicionar exclusão, buscas ou filtros ausentes na API.
- CI mantém o check obrigatório Build e testes e executa formatação, testes e build Angular antes do verify Java. Usar Node oficial fixado por SHA de Action e arquivo de versão.

## Consequências

- Dois processos locais: API 8080 e Angular 4200. O JAR Spring continua servindo somente a API.
- Credenciais precisam ser informadas novamente após reload. O botão Sair limpa o cliente, sem prometer invalidar uma sessão de autenticação no servidor Basic.
- O proxy existe apenas no desenvolvimento. Uma futura publicação exigirá arquitetura e segurança próprias; não faz parte do escopo.
- Testes HTTP/componentes cobrem sessão, headers, erros, formulário e paginação. A validação real do navegador complementa, mas não substitui, esses testes.

Referências: [compatibilidade Angular](https://angular.dev/reference/versions), [interceptors](https://angular.dev/guide/http/interceptors), [testes HTTP](https://angular.dev/guide/http/testing) e [proxy local](https://angular.dev/tools/cli/serve#proxying-to-a-backend-server).
