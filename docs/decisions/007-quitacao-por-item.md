# ADR-007 — Quitação como transição do item devolvido

Estado: contrato aprovado antes da implementação de PAG-001.

## Contexto e alternativas

O sistema local registra pagamentos recebidos pelo responsável. As regras permitem quitar itens separadamente, mas não dividir o valor de um item. Um módulo de processamento bancário ou um livro-caixa com parcelas não atende a uma necessidade atual.

## Decisão

Registrar a quitação no estado do item da locação, preenchendo somente dataPagamento depois de verificar devolução, valor fixado e ausência de quitação anterior. Receber somente IDs; o servidor determina valor e data.

O serviço de pagamento permanece junto ao agregado de locações, com controller/DTOs próprios e sem alterar o cálculo de devolução. Reutilizar V4. Caso surjam estornos, conciliação, múltiplos recebimentos ou integração bancária, reavaliar um modelo de eventos/recebimentos separado, sem antecipá-lo agora.

Usar transação integral por lote e o mesmo protocolo de bloqueios de abertura/devolução. Consultar clienteId antes do lock e os itens somente depois dele. Pagamento repetido ou concorrente não sobrescreve o registro anterior.

Derivar encerrada na consulta detalhada somente quando todos os itens estiverem devolvidos e pagos. A elegibilidade para nova retirada permanece uma consulta de todas as pendências do cliente, incluindo outras locações e atrasos.

## Consequências

- Nenhum valor é recalculado ou informado pelo cliente HTTP; somar apenas os totais já preservados dos itens selecionados.
- Um lote pode quitar alguns itens e deixar outros pendentes, sem aceitar pagamento parcial de um mesmo item.
- Pagamento não libera nem prende cópias; disponibilidade continua derivada do empréstimo ativo.
- O encerramento físico e financeiro não exige uma segunda flag persistida.
- Não há gateway, cobrança automática, estorno, alteração de pagamento ou comprovante bancário nesta entrega.
