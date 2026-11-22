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

- FRONT-002: livros, exemplares e disponibilidade.
- FRONT-003: abertura/detalhe de locações, devolução e quitação por item.
- Consolidar validação do fluxo completo quando essas telas existirem. Não declarar o frontend completo antes disso.
