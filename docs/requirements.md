# Requisitos — Locadora de livros

Estado: recorte funcional do MVP aprovado. MVP backend entregue pela API; evolução Angular aprovada em FRONT-001, inicialmente login e Clientes. Arquitetura e stack aprovadas; execução exclusivamente local para portfólio no GitHub. Autenticação HTTP Basic local e fuso America/Sao_Paulo aprovados; requisitos não funcionais adicionais serão detalhados conforme a entrega.

## Contexto confirmado
- Aplicação com Java + Spring Boot para uma locadora de livros físicos.
- Qualquer cliente pode locar, sem associação ou condição de sócio.
- Um responsável opera o sistema e registra as operações.
- Objetivo do projeto: aprendizado e portfólio de engenharia de software no GitHub, com execução local reproduzível. Não haverá implantação em produção no escopo atual.

## Regras confirmadas
- RN-01: tabela única de preços, independentemente do título, apenas por diária.
- RN-02: diária inicial de R$ 2,00 por exemplar; valor estipulado por autonomia concedida pelo usuário, sem pesquisa de mercado.
- RN-03: multa adicional fixa de R$ 1,00 por dia de atraso, por exemplar.
- RN-04: cobrança por datas, sem considerar horários. Dias cobrados = máximo entre 1 e a diferença entre devolução efetiva e retirada.
- RN-05: dias de atraso = máximo entre 0 e a diferença entre devolução efetiva e devolução prevista. Devolução na data prevista não gera multa.
- RN-06: as diárias continuam durante o atraso. Total por exemplar = dias cobrados × diária + dias de atraso × multa.
- RN-07: pagamento previsto na devolução, com base no valor final; é permitido devolver sem pagar e quitar posteriormente.
- RN-08: uma locação pode conter vários exemplares, com devolução e pagamento individualizados. A previsão de devolução é controlada por exemplar.
- RN-09: devoluções parciais não encerram os empréstimos dos demais exemplares; suas cobranças continuam até as respectivas devoluções.
- RN-10: ao devolver um exemplar, calcular e fixar o valor devido até a data da devolução e liberá-lo para nova locação. Nenhuma nova diária ou multa é gerada para esse empréstimo após a devolução, mesmo com dívida pendente.
- RN-11: manter a situação de devolução separada da situação financeira. A locação fica integralmente encerrada quando todos os exemplares forem devolvidos e os pagamentos quitados.
- RN-12: bloquear novas locações para clientes com valor pendente de pagamento de exemplares já devolvidos. Permitir devoluções e quitações normalmente. A quitação remove o impedimento financeiro; a liberação de novas locações também depende da ausência de exemplares atrasados ainda não devolvidos (RN-14). Valores em andamento de exemplares ainda emprestados não constituem, por si só, dívida vencida para este bloqueio.

- RN-13: no MVP, a quitação é integral por exemplar. É permitido quitar alguns exemplares e manter outros pendentes; não aceitar pagamento parcial nem parcelamento do valor devido por um mesmo exemplar.

- RN-14: bloquear novas locações se o cliente possuir qualquer exemplar não devolvido cuja previsão de devolução seja anterior à data atual. Manter devoluções e quitações permitidas. Liberar novas locações somente quando não houver exemplares atrasados nem dívidas de exemplares devolvidos. Exemplares não devolvidos ainda dentro do prazo não impedem a liberação.

## Cadastros — campos aprovados
- Cliente: nome, telefone e CPF obrigatórios; e-mail opcional. CPF único por cliente, com validação dos dígitos verificadores. Aceitar entrada com ou sem pontuação e armazenar apenas os números, preservando zeros à esquerda. A validação não comprova identidade nem situação cadastral.
- Livro: título, autor e ISBN opcional.
- Exemplar: código interno único para identificar cada cópia física de um livro.
- Não incluir CPFs de clientes reais em exemplos, testes, logs ou documentação versionada.

## Exemplos de aceitação do cálculo
| Retirada | Previsão | Devolução | Diárias | Dias de multa | Total |
|---|---|---|---:|---:|---:|
| 10/01/2027 | 10/01/2027 | 10/01/2027 | 1 | 0 | R$ 2,00 |
| 10/01/2027 | 12/01/2027 | 12/01/2027 | 2 | 0 | R$ 4,00 |
| 10/01/2027 | 12/01/2027 | 14/01/2027 | 4 | 2 | R$ 10,00 |

## MVP — recorte aprovado
- Cadastro de clientes, títulos e exemplares físicos individualizados.
- Registro de locação com cliente e múltiplos exemplares, com retirada e previsão de devolução por exemplar.
- Impedir locações simultâneas do mesmo exemplar.
- Devolução parcial ou total com cálculo por exemplar, registro do pagamento recebido ou manutenção de pendência financeira.
- Consulta de disponibilidade, locações abertas, atrasadas e histórico.

## Decisões complementares aprovadas
- Preservar a diária e a multa vigentes na abertura de cada locação, evitando alteração retroativa de preços.
- Apenas registrar pagamentos recebidos, sem integração com meios de pagamento no MVP.
- Começar pela API backend com Spring Boot. Adiar as telas e validar os fluxos por requisições HTTP e testes automatizados.

## Questões abertas
- Definir tratamento de cancelamento e correção de registros antes de implementá-los; não há autorização para apagar histórico de locações ou pagamentos.
- Detalhar contratos da API e requisitos não funcionais das próximas funcionalidades. Autenticação, execução local e fuso estão aprovados no ADR-003.
- Quitação por exemplar implementada em PAG-001; preservar seu contrato nas futuras telas de pagamento.

## Critérios de aceitação — devolução e pendência financeira
- Dada uma locação com três exemplares, ao devolver apenas um, liberar esse exemplar e manter os outros dois emprestados, com suas cobranças em andamento.
- Ao devolver sem pagar, registrar a data efetiva, o valor final devido e a pendência financeira; o exemplar fica disponível novamente.
- Se o pagamento ocorrer dias após a devolução, manter o valor fixado na devolução, sem novas diárias ou multas por esse intervalo.
- Com todos os exemplares devolvidos e algum pagamento pendente, indicar devolução completa e pendência financeira, sem marcar a locação como integralmente encerrada.
- Após devolver todos os exemplares e quitar todos os valores, indicar encerramento integral.

## Critérios de aceitação — bloqueio financeiro
- Dado um cliente com valor não quitado de exemplar devolvido, rejeitar nova locação, informando a pendência e sem registrar a retirada de qualquer exemplar.
- Mesmo com bloqueio financeiro, permitir devolução dos demais exemplares e registro de quitação.
- Após quitar todas as pendências de exemplares devolvidos, remover o impedimento financeiro para novas locações.
- Uma locação ainda em andamento, dentro do prazo e sem dívida de exemplares devolvidos, não gera bloqueio financeiro apenas por ter pagamento previsto para a devolução.

## Critérios de aceitação — quitação por exemplar
- Dado um exemplar devolvido com dívida de R$ 10,00, aceitar a quitação de R$ 10,00 e rejeitar pagamento parcial de R$ 5,00, sem alterar sua pendência.
- Dados dois exemplares devolvidos com dívidas de R$ 10,00 e R$ 4,00, permitir quitar apenas o primeiro; manter R$ 4,00 pendentes e o bloqueio de novas locações.
- Não permitir nova quitação de uma dívida já quitada.

## Critérios de aceitação — bloqueio por atraso
- Na data prevista de devolução, um exemplar ainda não devolvido não gera bloqueio por atraso; no dia seguinte, gera bloqueio.
- Se houver exemplar atrasado ainda não devolvido, rejeitar nova locação sem registrar retiradas, mesmo sem dívida de exemplar já devolvido.
- Após devolver o exemplar atrasado sem pagar, manter o bloqueio por pendência financeira.
- Após devolver todos os exemplares atrasados e quitar todas as dívidas, permitir nova locação, mesmo com outros exemplares emprestados dentro do prazo.
- Enquanto houver atraso ou dívida, permitir devoluções e quitações normalmente.


## Critérios de aceitação — CPF do cliente
- Rejeitar cadastro sem CPF, com quantidade inválida de dígitos, dígitos verificadores inválidos ou sequência de dígitos todos iguais.
- Aceitar CPF válido com ou sem pontuação e persistir a mesma representação numérica, preservando zeros à esquerda.
- Rejeitar criação ou alteração que atribua a outro cliente um CPF já cadastrado, inclusive quando a entrada usar pontuação diferente.
- Permitir atualizar o próprio cliente sem considerar seu CPF atual uma duplicidade.

## Fora do MVP — evolução futura
- Telas de Catálogo/Locações após FRONT-001; a primeira entrega Angular abrange login e Clientes.
- Reservas de exemplares.
- Renovação ou alteração do prazo de locações em andamento.
- Tratamento de perda ou dano de exemplares. A operação inicial cobre devoluções normais; esses casos excepcionais ainda não terão fluxo no sistema.

## Critérios de aceitação — preservação de preços
- Ao abrir uma locação, registrar a diária e a multa vigentes para seus exemplares.
- Se a tabela mudar depois, calcular a devolução de uma locação existente com os preços registrados na abertura.
- Aplicar os novos preços somente às locações abertas após a alteração.

## Evolução aprovada — interface Angular
Em 16/11/2026, o usuário aprovou FRONT-001: estrutura Angular no mesmo repositório, autenticação do operador e listagem/cadastro/edição de Clientes. Preservar os contratos e a segurança da API. Catálogo e Locações em entregas posteriores; requisitos e critérios em [frontend.md](frontend.md) e ADR-009. Helper de PowerShell e colinhas manuais permanecem locais, sem publicação, conforme solicitação do usuário.
