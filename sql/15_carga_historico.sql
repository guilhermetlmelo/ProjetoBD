-- =====================================================================
-- Projeto BD - Bar | Etapa 06: carga de historico (02/07 a 18/09/2026)
--
-- Os dados das Etapas 02 a 05 cobrem so 7 noites de setembro, pouco para
-- analisar tendencia no dashboard. Este script lanca o movimento das
-- quintas a domingos de 02/07 a 18/09. Nas noites de setembro que ja tinham
-- atendimentos lancados a mao, so completa o que falta para o movimento
-- normal da noite. O fim de semana de 19 e 20/09 nao e tocado, entao nada
-- do que ja existia muda (as contas abertas de 20/09 continuam sendo as
-- mais recentes e as consultas das etapas anteriores dao o mesmo resultado).
--
-- Regras seguidas:
--   * setseed deixa a carga deterministica: todo mundo do grupo gera os
--     mesmos numeros ao rodar o rodar_tudo.ps1;
--   * o cliente so aparece depois da Data_Primeira_Visita dele;
--   * as mesas que eram ociosas (consulta de anti join) continuam sem uso;
--   * a conta e aberta como ABERTA, recebe os pedidos e os pagamentos e so
--     depois vira FECHADA, porque o trigger trg_pedido_conta_fechada barra
--     pedido em conta fechada;
--   * o total pago e igual ao consumido (fn_valor_conta), dividido em 1 a 3
--     pagamentos parciais;
--   * IDs a partir de 1001, para separar do lancamento manual.
-- =====================================================================

DO $$
DECLARE
    -- mesas usadas nos dados originais (as ociosas ficam de fora)
    v_mesas    INTEGER[] := ARRAY[1,2,3,4,5,6,7,8,9,10,13,14,15,16,19,20,21,25,26,27];
    -- quem atende mesa
    v_garcons  VARCHAR[] := ARRAY['20000000004','20000000003','20000000019','20000000013',
                                  '20000000002','20000000007','20000000012','20000000017',
                                  '20000000014','20000000009','20000000011','20000000010',
                                  '20000000015','20000000008','20000000016'];
    -- produtos com repeticao para dar peso aos mais pedidos (cerveja e chopp saem mais)
    v_produtos INTEGER[] := ARRAY[1,1,1,2,2,3,3,3,4,4,5,
                                  6,6,6,7,7,7,8,8,9,10,
                                  11,11,12,13,14,15,
                                  16,16,16,17,17,18,19,20,
                                  21,21,22,22,23,24,25,
                                  26,26,26,27,27,28,29,30,30];
    v_metodos  VARCHAR[] := ARRAY['PIX','PIX','PIX','CREDITO','CREDITO','DEBITO','DEBITO','DINHEIRO'];

    v_dia        DATE;
    v_semana     INTEGER;
    v_qtd_dia    INTEGER;
    v_id         INTEGER := 1000;
    v_abertura   TIMESTAMP;
    v_cliente    VARCHAR(11);
    v_rodadas    INTEGER;
    v_itens      INTEGER;
    v_prod       INTEGER;
    v_hora_ped   TIMESTAMP;
    v_total      NUMERIC(10,2);
    v_partes     INTEGER;
    v_parcela    NUMERIC(10,2);
    v_pago       NUMERIC(10,2);
    v_saida      TIMESTAMP;
    r            INTEGER;
    i            INTEGER;
    k            INTEGER;
BEGIN
    PERFORM setseed(0.2026);

    FOR v_dia IN
        SELECT d::DATE
          FROM generate_series(DATE '2026-07-02', DATE '2026-09-18', INTERVAL '1 day') AS g(d)
         WHERE EXTRACT(ISODOW FROM d) IN (4, 5, 6, 7)   -- quinta a domingo
    LOOP
        v_semana := (v_dia - DATE '2026-07-02') / 7;    -- 0 a 11

        -- movimento base por dia da semana, crescendo um pouco a cada semana
        v_qtd_dia := CASE EXTRACT(ISODOW FROM v_dia)
                         WHEN 4 THEN 2
                         WHEN 5 THEN 4
                         WHEN 6 THEN 5
                         ELSE 3
                     END
                     + v_semana / 3
                     + floor(random() * 3)::INTEGER;

        -- desconta o que ja foi lancado a mao nessa noite
        v_qtd_dia := GREATEST(v_qtd_dia - (SELECT COUNT(*) FROM ATENDIMENTO
                                            WHERE CAST(Data_Hora_Abertura AS DATE) = v_dia), 0);

        FOR i IN 1 .. v_qtd_dia LOOP
            v_id := v_id + 1;

            -- domingo abre mais cedo
            v_abertura := v_dia
                          + make_interval(hours => CASE WHEN EXTRACT(ISODOW FROM v_dia) = 7 THEN 16 ELSE 18 END)
                          + make_interval(mins => (floor(random() * 300))::INTEGER);

            SELECT c.CPF
              INTO v_cliente
              FROM CLIENTE c
             WHERE c.Data_Primeira_Visita <= v_dia
             ORDER BY random()
             LIMIT 1;

            INSERT INTO ATENDIMENTO (ID_Atendimento, Data_Hora_Abertura, Status_Conta,
                                     CPF_Cliente, CPF_Garcom, Numero_Mesa)
            VALUES (v_id, v_abertura, 'ABERTA', v_cliente,
                    v_garcons[1 + floor(random() * array_length(v_garcons, 1))::INTEGER],
                    v_mesas[1 + floor(random() * array_length(v_mesas, 1))::INTEGER]);

            -- 1 a 4 rodadas de pedido, cada uma com 1 a 3 produtos diferentes
            v_rodadas  := 1 + floor(random() * 4)::INTEGER;
            v_hora_ped := v_abertura + INTERVAL '5 minutes';

            FOR r IN 1 .. v_rodadas LOOP
                v_itens := 1 + floor(random() * 3)::INTEGER;
                FOR k IN 1 .. v_itens LOOP
                    v_prod := v_produtos[1 + floor(random() * array_length(v_produtos, 1))::INTEGER];
                    INSERT INTO PEDIDO (ID_Pedido, ID_Atendimento, Codigo_Prod, Quantidade, Data_Hora_Pedido)
                    VALUES (r, v_id, v_prod, 1 + floor(random() * 3)::INTEGER, v_hora_ped)
                    ON CONFLICT ON CONSTRAINT pk_pedido
                    DO UPDATE SET Quantidade = PEDIDO.Quantidade + 1;
                END LOOP;
                v_hora_ped := v_hora_ped + make_interval(mins => 25 + floor(random() * 30)::INTEGER);
            END LOOP;

            -- pagamento: o cliente sai entre 15 e 45 minutos depois do ultimo pedido
            v_total  := fn_valor_conta(v_id);
            v_partes := 1 + floor(random() * 3)::INTEGER;
            v_saida  := v_hora_ped + make_interval(mins => 15 + floor(random() * 30)::INTEGER);
            v_pago   := 0;

            FOR k IN 1 .. v_partes LOOP
                IF k < v_partes THEN
                    v_parcela := ROUND(v_total / v_partes, 2);
                ELSE
                    v_parcela := v_total - v_pago;
                END IF;
                v_pago := v_pago + v_parcela;

                INSERT INTO PAGAMENTO_PARCIAL (ID_Atendimento, Num_Transacao, Valor_Pago,
                                               Metodo_Pagamento, Horario_Pagamento)
                VALUES (v_id, k, v_parcela,
                        v_metodos[1 + floor(random() * array_length(v_metodos, 1))::INTEGER],
                        v_saida + make_interval(mins => k - 1));
            END LOOP;

            UPDATE ATENDIMENTO SET Status_Conta = 'FECHADA' WHERE ID_Atendimento = v_id;
        END LOOP;
    END LOOP;

    RAISE NOTICE '% atendimento(s) de historico lancado(s)', v_id - 1000;
END;
$$;

-- Conferencia: todas as contas do historico precisam estar QUITADAS
SELECT fn_situacao_conta(ID_Atendimento) AS situacao, COUNT(*) AS contas
  FROM ATENDIMENTO
 WHERE ID_Atendimento > 1000
 GROUP BY 1;
