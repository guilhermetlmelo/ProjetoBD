-- =====================================================================
-- Projeto BD - Bar | Etapa 05: Triggers
-- =====================================================================

-- ---------------------------------------------------------------------
-- Trigger 1 (log): trg_log_preco_produto
--
-- Justificativa: PEDIDO nao guarda o preco cobrado, o valor da conta e
-- calculado com o Preco_Atual. Se o preco muda e ninguem registra, nao da
-- mais para saber quanto um item custava na epoca do pedido. O trigger
-- grava em LOG_PRECO_PRODUTO todo preco alterado, com data e usuario.
-- ---------------------------------------------------------------------
CREATE TABLE LOG_PRECO_PRODUTO (
    ID_Log          SERIAL        NOT NULL,
    Codigo_Prod     INTEGER       NOT NULL,
    Preco_Antigo    NUMERIC(10,2) NOT NULL,
    Preco_Novo      NUMERIC(10,2) NOT NULL,
    Data_Alteracao  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Usuario         VARCHAR(50)   NOT NULL DEFAULT CURRENT_USER,
    CONSTRAINT pk_log_preco_produto PRIMARY KEY (ID_Log),
    CONSTRAINT fk_log_preco_produto FOREIGN KEY (Codigo_Prod)
        REFERENCES PRODUTO (Codigo_Prod)
        ON DELETE CASCADE
);

CREATE OR REPLACE FUNCTION fn_trg_log_preco_produto()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO LOG_PRECO_PRODUTO (Codigo_Prod, Preco_Antigo, Preco_Novo)
    VALUES (NEW.Codigo_Prod, OLD.Preco_Atual, NEW.Preco_Atual);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_log_preco_produto
AFTER UPDATE OF Preco_Atual ON PRODUTO
FOR EACH ROW
WHEN (OLD.Preco_Atual IS DISTINCT FROM NEW.Preco_Atual)
EXECUTE FUNCTION fn_trg_log_preco_produto();

-- ---------------------------------------------------------------------
-- Trigger 2: trg_pedido_conta_fechada
--
-- Justificativa: depois que a conta e fechada o cliente ja pagou e foi
-- embora. Um pedido lancado nela (por engano do garcom, por exemplo) muda
-- o valor da conta e ela fica com saldo devedor que ninguem vai pagar.
-- O trigger barra INSERT e UPDATE em PEDIDO quando o atendimento esta
-- FECHADA.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_trg_pedido_conta_fechada()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_status VARCHAR(10);
BEGIN
    SELECT Status_Conta
      INTO v_status
      FROM ATENDIMENTO
     WHERE ID_Atendimento = NEW.ID_Atendimento;

    IF v_status = 'FECHADA' THEN
        RAISE EXCEPTION 'A conta do atendimento % ja esta fechada', NEW.ID_Atendimento;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_pedido_conta_fechada
BEFORE INSERT OR UPDATE ON PEDIDO
FOR EACH ROW
EXECUTE FUNCTION fn_trg_pedido_conta_fechada();

-- Exemplos (rodar manualmente):
--
--   UPDATE PRODUTO SET Preco_Atual = 13.00 WHERE Codigo_Prod = 1;
--   SELECT * FROM LOG_PRECO_PRODUTO;
--
--   -- atendimento 1 esta FECHADA: da erro
--   INSERT INTO PEDIDO VALUES (3, 1, 1, 1, '2026-09-04 22:00:00');
