-- =====================================================================
-- Projeto BD - Bar | Etapa 05: Procedimentos
-- =====================================================================

-- ---------------------------------------------------------------------
-- Procedimento 1 (atualizacao): sp_reajustar_preco_categoria
--
-- Justificativa: o bar reajusta o cardapio por categoria (ex.: quando o
-- fornecedor de cerveja aumenta o preco). O procedimento aplica o
-- percentual em todos os produtos da categoria de uma vez, validando os
-- parametros. Cada mudanca de preco fica registrada pelo trigger
-- trg_log_preco_produto (sql/14_triggers.sql).
-- ---------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_reajustar_preco_categoria(
    p_categoria  VARCHAR,
    p_percentual NUMERIC
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_qtd INTEGER;
BEGIN
    IF p_percentual <= -100 THEN
        RAISE EXCEPTION 'Percentual invalido: %', p_percentual;
    END IF;

    UPDATE PRODUTO
       SET Preco_Atual = ROUND(Preco_Atual * (1 + p_percentual / 100), 2)
     WHERE Categoria = p_categoria;

    GET DIAGNOSTICS v_qtd = ROW_COUNT;

    IF v_qtd = 0 THEN
        RAISE EXCEPTION 'Nenhum produto na categoria %', p_categoria;
    END IF;

    RAISE NOTICE '% produto(s) da categoria % reajustado(s) em %', v_qtd, p_categoria, p_percentual || '%';
END;
$$;

-- ---------------------------------------------------------------------
-- Procedimento 2 (cursor): sp_redistribuir_sem_supervisor
--
-- Justificativa: a FK de supervisor em FUNCIONARIO e ON DELETE SET NULL,
-- entao quando um gerente sai do bar a equipe dele fica sem supervisor.
-- O procedimento percorre esses funcionarios com um cursor e entrega cada
-- um ao gerente que tem MENOS supervisionados naquele momento.
--
-- Por que precisa de cursor: a escolha do gerente de cada funcionario
-- depende das atribuicoes feitas nas linhas anteriores (a carga de cada
-- gerente muda a cada passo). Um UPDATE unico calcula tudo com a contagem
-- de antes da execucao e mandaria todo mundo para o mesmo gerente.
-- ---------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_redistribuir_sem_supervisor()
LANGUAGE plpgsql
AS $$
DECLARE
    cur_func CURSOR FOR
        SELECT f.CPF, p.Nome
          FROM FUNCIONARIO f
          JOIN PESSOA p ON p.CPF = f.CPF
         WHERE f.CPF_Supervisor IS NULL
           AND f.Cargo <> 'Gerente'
         ORDER BY f.Matricula;

    v_func    RECORD;
    v_gerente VARCHAR(11);
    v_qtd     INTEGER := 0;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM FUNCIONARIO WHERE Cargo = 'Gerente') THEN
        RAISE EXCEPTION 'Nao ha gerente cadastrado';
    END IF;

    OPEN cur_func;
    LOOP
        FETCH cur_func INTO v_func;
        EXIT WHEN NOT FOUND;

        SELECT g.CPF
          INTO v_gerente
          FROM FUNCIONARIO g
          LEFT JOIN FUNCIONARIO s ON s.CPF_Supervisor = g.CPF
         WHERE g.Cargo = 'Gerente'
         GROUP BY g.CPF
         ORDER BY COUNT(s.CPF), g.CPF
         LIMIT 1;

        UPDATE FUNCIONARIO
           SET CPF_Supervisor = v_gerente
         WHERE CPF = v_func.CPF;

        v_qtd := v_qtd + 1;
        RAISE NOTICE '% -> supervisor %', v_func.Nome, v_gerente;
    END LOOP;
    CLOSE cur_func;

    RAISE NOTICE '% funcionario(s) redistribuido(s)', v_qtd;
END;
$$;

-- Exemplos (rodar manualmente):
--
--   CALL sp_reajustar_preco_categoria('Cerveja', 10);
--
--   -- simula duas equipes que ficaram sem supervisor
--   UPDATE FUNCIONARIO SET CPF_Supervisor = NULL
--    WHERE CPF_Supervisor IN ('20000000001', '20000000005');
--   CALL sp_redistribuir_sem_supervisor();
