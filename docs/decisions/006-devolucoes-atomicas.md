# ADR-006 — Devolução integral do lote e cobrança fixada

Estado: regras aprovadas antes da implementação da DEV-001.

## Contexto

Uma locação pode ter devoluções em datas distintas. O cliente pode devolver sem pagar, inclusive quando estiver bloqueado para novas retiradas. A mesma cópia pode ser alugada novamente após sua devolução; repetir a devolução de um empréstimo antigo não pode liberar essa nova retirada.

## Alternativas e decisão

- Retornar pelo id do exemplar seria ambíguo ao longo do histórico. Identificar pelo id do item da locação, verificando seu vínculo com a locação informada.
- Ignorar repetições ocultaria erro operacional; conforme aprovado, retornar 409 para item já devolvido, preservando data/valor anteriores.
- Processar itens separadamente permitiria devoluções parciais acidentais. Usar uma única transação para o lote, calculando todos os valores antes de alterar qualquer item.
- Preservar o protocolo do ADR-005: consultar primeiro apenas o clienteId da locação, bloquear o cliente e então carregar os itens atuais; depois bloquear os exemplares por UUID ordenado. Não carregar itens antes de aguardar o lock do cliente, evitando estado desatualizado no contexto de persistência.
- Calcular com a data atual do Clock e as tarifas preservadas no item, usando BigDecimal. Não aplicar tarifas correntes, arredondamento silencioso ou valores/datas enviados pelo cliente da API.
- Gravar dataDevolucao e valorTotal, sem alterar dataPagamento. Reutilizar as colunas da V4; nenhuma alteração em migração aplicada.

## Consequências

- Cópia devolvida fica disponível, enquanto a dívida mantém o cliente bloqueado para novas retiradas. Não existe flag de disponibilidade a sincronizar.
- O lote não altera itens não selecionados. Entrada inválida, item de outra locação ou cálculo acima do limite NUMERIC(12,2) desfaz a operação inteira.
- Devoluções concorrentes respeitam o estado atual após o lock. Quando há sobreposição, uma operação pode concluir e a outra deve rejeitar sem atualizar seus itens restantes.
- Situação física não equivale a quitação. PAG-001 implementará o pagamento integral por item e manterá o mesmo protocolo de bloqueios.
- Consultas da locação exibem a data efetiva, o total fixado e a data de pagamento por item; não prolongar a cobrança após a devolução.
