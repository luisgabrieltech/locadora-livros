# Interface da locadora

Operadores podem listar, cadastrar e editar clientes, livros e exemplares. O catálogo mostra a disponibilidade de cada exemplar. As operações de locação estão disponíveis pela API.

## Rodar

Node 24.19.0 (arquivo .node-version) e npm. Não instalar Angular CLI globalmente; os scripts usam a versão local do projeto.

Primeiro iniciar o backend e o banco conforme o [README principal](../README.md). Depois, nesta pasta:

```powershell
npm.cmd ci
npm.cmd start
```

Abrir http://127.0.0.1:4200. No Linux/macOS, usar npm no lugar de npm.cmd. Entrar com APP_USERNAME e APP_PASSWORD configurados no .env do backend. Não copiar esse arquivo para frontend nem inserir senhas em código ou configurações do Angular.

O proxy encaminha /api para http://127.0.0.1:8080, preservando cookie e CSRF. Se a API não estiver rodando, o login apresenta erro. O proxy é configuração do servidor de desenvolvimento, não é incluído como servidor no build estático.

## Verificar

```powershell
npm.cmd run format:check
npm.cmd test
npm.cmd run build
```

Testes com Vitest e backend HTTP simulado, sem exigir Java ou Docker para os testes unitários do frontend. O build gera dist/frontend/browser. Não há ng e2e configurado nem suíte Playwright versionada nesta entrega; a verificação real no navegador está registrada em [docs/frontend.md](../docs/frontend.md).

## Autenticação

Basic e token CSRF ficam apenas em memória. Recarregar a página exige novo login. Sair limpa o estado do frontend; a API continua autenticando cada chamada por Basic. Nenhuma senha é gravada em localStorage, sessionStorage ou bundle. O cookie conserva somente o estado CSRF do servidor.

Formulários apresentam validações de formato e mensagens retornadas pela API; unicidade e dígitos verificadores do CPF continuam validados no backend. A listagem não contém CPF. Apenas abrir a edição busca o detalhe com os campos necessários.
