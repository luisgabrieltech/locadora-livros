# FRONT-001 — Base Angular, login e Clientes

## Objetivo e escopo aprovado

Permitir ao responsável autenticar-se e listar/cadastrar/editar clientes pela interface local. O usuário aprovou essa primeira entrega; Catálogo e Locações ficam para FRONT-002/003. Sem alteração do backend ou do modelo de dados.

## Critérios de aceitação

- Login obrigatório e feedback de formulário incompleto, credenciais inválidas e API indisponível.
- Não persistir credenciais em storage ou arquivos do frontend. Sair/recarregar exige novo login; chamadas externas não recebem headers de autenticação.
- Cookie e CSRF preservados; Basic em cada chamada à API. Exibir erros 400/403/409 sem repetição automática de escrita.
- Clientes paginados com navegação de página e tamanho, estado vazio, carregamento e erro com opção de tentar novamente.
- Cadastro e edição de nome, telefone, CPF e email opcional. Não exibir CPF na listagem; a edição busca os dados do detalhe. Omitir/remover email gera ausência, conforme PUT da API.
- Impedir envio duplicado durante gravação e preservar formulário em erros. Retornar foco ao campo inicial e permitir navegação por teclado no modal.
- Layout utilizável em desktop e celular, sem rolagem horizontal da página; tabelas longas podem rolar dentro do painel.
- Formatação, testes, build e check CI existentes aprovados antes da integração.

## Organização e execução

frontend/src/app/core contém estado de autenticação, guard, interceptor e mensagens; login e clientes contêm suas telas e formulários. ClientesService centraliza chamadas tipadas da API. Não há estado de negócio duplicado no frontend.

Com API e banco ativos, executar em frontend: npm ci e npm start. Abrir http://127.0.0.1:4200. Usar credenciais APP_USERNAME/APP_PASSWORD do .env do backend. Detalhes e comandos no [README do frontend](../frontend/README.md). Decisões e limites no [ADR-009](decisions/009-frontend-angular-local.md).

## Validação

Estado: implementação validada localmente e preparada para PR; conferir a CI do último SHA antes de integrar.

- npm run format:check aprovado; npm test aprovou 19 testes em três arquivos (7 autenticação/interceptor, 3 login e 9 Clientes), com JUnit em frontend/test-results/vitest.xml; npm run build aprovado. Bundle inicial de produção: aproximadamente 275 kB bruto/75 kB estimados de transferência, sem exceder o budget configurado. Esses tamanhos não são benchmark de latência.
- Fluxo real executado em Edge headless contra API/PostgreSQL locais: login inválido/válido, CPF inválido, cadastro, duplicidade 409, edição, remoção de email, logout e rota protegida. Criado um cliente explicitamente fictício com nome iniciado por AAA Teste Interface; dados existentes preservados. Não foram executados devolução/pagamento pela UI, pois essas telas não fazem parte desta entrega.
- Inspeção visual de desktop 1440px e celular 390px. Axe com regras WCAG 2A/2AA/2.1AA nas telas de login, Clientes e formulário em ambos os tamanhos: nenhuma violação encontrada após corrigir contraste e overflow da tabela. Isso não certifica conformidade integral; a verificação é limitada às telas e estados inspecionados.
- Verificados foco inicial do modal, fechamento por Escape, ausência de CPF na tabela, storage vazio, email removido efetivamente no backend e necessidade de login após reload.
- npm audit --omit=dev --audit-level=high executado: zero vulnerabilidades reportadas nas dependências de execução. Dependências de desenvolvimento e backend não foram analisadas por esse comando.
- Ferramentas de QA e screenshots ficaram em target, fora do Git; testes do frontend usam somente dados fictícios. Nenhuma credencial ou .env foi incluído no bundle/repositório.

## Próximas entregas

- FRONT-002: catálogo implementado. Veja a validação abaixo.
- FRONT-003: consulta e abertura implementadas. Devolução e quitação por item seguem previstas.
- Consolidar validação do fluxo completo quando essas telas existirem. Não declarar o frontend completo antes disso.

## FRONT-002 — Catálogo

A rota protegida /catalogo dá acesso às seções Livros e Exemplares. Ambas consultam páginas da API e permitem cadastro e edição. Para vincular um exemplar, o operador escolhe um título em uma lista paginada. A lista de exemplares mostra o título associado e a disponibilidade retornada pela API. A interface conserva os dados do formulário quando a API rejeita ISBN, código ou alteração de vínculo. O [contrato do catálogo](catalogo-api.md) descreve essas regras.

Em 22/11/2026, `npm run format:check`, `npm test` e `npm run build` passaram com o Node 26.13.1 instalado localmente. A suíte aprovou 23 testes, quatro deles do catálogo. Os testes novos cobrem remoção e rejeição de ISBN, seleção de livro em outra página, exibição de disponibilidade e conflitos por código duplicado ou histórico de locação. A versão configurada para o projeto permanece a indicada no [README do frontend](../frontend/README.md).

Em 22/11/2026, o teste em Brave com viewport de 390 px, API e PostgreSQL locais confirmou login, acesso ao catálogo, rejeição de ISBN inválido, cadastro de livro, remoção de ISBN na edição e cadastro de exemplar vinculado. A listagem mostrou o título, o código e a situação Disponível sem rolagem horizontal da página. O livro e o exemplar fictícios ficaram no banco local.

## FRONT-003 — Consulta de locações

A rota protegida /locacoes lista as locações abertas por padrão. O operador pode paginar e filtrar por situação, incluindo atrasos, pagamentos pendentes e histórico completo. Atraso e pagamento pendente podem aparecer ao mesmo tempo. A consulta de itens mostra o identificador de cada exemplar, a previsão de devolução e as datas registradas de devolução e pagamento. [Contrato de consultas](consultas-locacoes-api.md)

Em **Nova locação**, o operador escolhe um cliente e exemplares disponíveis em listas paginadas e informa a previsão de devolução de cada cópia. A API define a data de retirada e os preços e aplica as regras de disponibilidade e bloqueio. Em caso de conflito, a interface conserva a seleção e os prazos. [Contrato de abertura](locacoes-api.md)
