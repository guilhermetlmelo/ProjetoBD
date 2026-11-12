-- =====================================================================
-- Projeto BD - Bar | Etapa 05: Funcoes
-- =====================================================================

-- ---------------------------------------------------------------------
-- Funcao 1: fn_valor_conta(id_atendimento)
--
-- Justificativa: PEDIDO nao guarda o valor cobrado, entao o total da conta
-- sempre precisa ser calculado somando Quantidade * Preco_Atual dos itens.
-- Essa regra aparece no fechamento da conta, na conferencia com os
-- pagamentos e nos relatorios, entao faz sentido ter ela num lugar so.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_valor_conta(p_id_atendimento INTEGER)
RETURNS NUMERIC(10,2)
LANGUAGE plpgsql
AS $$
DECLARE
    v_total NUMERIC(10,2);
BEGIN
    SELECT COALESCE(SUM(pe.Quantidade * pr.Preco_Atual), 0)
      INTO v_total
      FROM PEDIDO pe
      JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
     WHERE pe.ID_Atendimento = p_id_atendimento;

    RETURN v_total;
END;
$$;

-- ---------------------------------------------------------------------
-- Funcao 2: fn_situacao_conta(id_atendimento)
--
-- Justificativa: como a conta pode ser paga em varias partes
-- (PAGAMENTO_PARCIAL), o caixa precisa saber se ela ja esta quitada antes
-- de liberar a mesa. A funcao compara o consumido com o que ja foi pago e
-- devolve a situacao: SEM CONSUMO, EM ABERTO, PARCIAL, QUITADA ou
-- PAGO A MAIS (troco/gorjeta).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_situacao_conta(p_id_atendimento INTEGER)
RETURNS VARCHAR(15)
LANGUAGE plpgsql
AS $$
DECLARE
    v_consumido NUMERIC(10,2);
    v_pago      NUMERIC(10,2);
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ATENDIMENTO WHERE ID_Atendimento = p_id_atendimento) THEN
        RAISE EXCEPTION 'Atendimento % nao existe', p_id_atendimento;
    END IF;

    v_consumido := fn_valor_conta(p_id_atendimento);

    SELECT COALESCE(SUM(Valor_Pago), 0)
      INTO v_pago
      FROM PAGAMENTO_PARCIAL
     WHERE ID_Atendimento = p_id_atendimento;

    IF v_consumido = 0 THEN
        RETURN 'SEM CONSUMO';
    ELSIF v_pago = 0 THEN
        RETURN 'EM ABERTO';
    ELSIF v_pago < v_consumido THEN
        RETURN 'PARCIAL';
    ELSIF v_pago = v_consumido THEN
        RETURN 'QUITADA';
    ELSE
        RETURN 'PAGO A MAIS';
    END IF;
END;
$$;

-- Exemplos
SELECT a.ID_Atendimento,
       a.Status_Conta,
       fn_valor_conta(a.ID_Atendimento)    AS valor_conta,
       fn_situacao_conta(a.ID_Atendimento) AS situacao
  FROM ATENDIMENTO a
 ORDER BY a.ID_Atendimento
 LIMIT 10;
