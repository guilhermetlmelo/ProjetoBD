-- =====================================================================
-- Projeto BD - Bar | Etapa 04: Visoes
-- =====================================================================

-- ---------------------------------------------------------------------
-- Visao 1: vw_itens_vendidos (6 tabelas, 5 JOINs)
--
-- Justificativa: o item vendido e o "fato" central do bar. Toda analise de
-- vendas (por categoria, por dia, por garcom, por area, por cliente) precisa
-- refazer o mesmo JOIN PEDIDO x PRODUTO x ATENDIMENTO x MESA x PESSOA x
-- FUNCIONARIO e repetir a regra do valor do item (Quantidade * Preco_Atual,
-- ja que PEDIDO nao guarda o preco cobrado). A visao centraliza essa regra e
-- entrega cada item ja com o contexto do atendimento: quem pediu, quem
-- atendeu, onde e quando.
-- Data_Atendimento e a data de ABERTURA da conta: um pedido feito a 00h30
-- pertence a noite em que a conta foi aberta, nao ao dia seguinte.
-- ---------------------------------------------------------------------
CREATE VIEW vw_itens_vendidos AS
SELECT pe.ID_Atendimento,
       pe.ID_Pedido,
       pe.Data_Hora_Pedido,
       CAST(a.Data_Hora_Abertura AS DATE)  AS Data_Atendimento,
       a.Status_Conta,
       pr.Codigo_Prod,
       pr.Nome_Produto,
       pr.Categoria,
       pe.Quantidade,
       pr.Preco_Atual                      AS Preco_Unitario,
       pe.Quantidade * pr.Preco_Atual      AS Valor_Item,
       m.Numero_Mesa,
       m.Localizacao,
       a.CPF_Cliente,
       pc.Nome                             AS Cliente,
       a.CPF_Garcom,
       pg.Nome                             AS Garcom,
       f.Cargo                             AS Cargo_Garcom
  FROM PEDIDO pe
  JOIN PRODUTO pr     ON pr.Codigo_Prod = pe.Codigo_Prod
  JOIN ATENDIMENTO a  ON a.ID_Atendimento = pe.ID_Atendimento
  JOIN MESA m         ON m.Numero_Mesa = a.Numero_Mesa
  JOIN PESSOA pc      ON pc.CPF = a.CPF_Cliente
  JOIN FUNCIONARIO f  ON f.CPF = a.CPF_Garcom
  JOIN PESSOA pg      ON pg.CPF = f.CPF;

-- ---------------------------------------------------------------------
-- Visao 2: vw_historico_cliente (5 tabelas, 4 JOINs + subconsultas)
--
-- Justificativa: e a "ficha" do cliente para o programa de fidelidade. A
-- gerencia decide quem merece fidelidade olhando frequencia, gasto e
-- preferencias, dados espalhados por CLIENTE, PESSOA, TELEFONE,
-- ATENDIMENTO, PEDIDO e PRODUTO. Os LEFT JOINs mantem na lista quem se
-- cadastrou e ainda nao consumiu. A coluna Segmento classifica o cliente pela
-- frequencia e Sugestao aponta quem deveria ganhar a fidelidade (2+ visitas ou
-- R$ 300+ gastos) ou ter a fidelidade revista (1 visita e menos de R$ 100).
-- ---------------------------------------------------------------------
CREATE VIEW vw_historico_cliente AS
SELECT c.CPF,
       p.Nome,
       (SELECT STRING_AGG(t.Telefone, ', ' ORDER BY t.Telefone)
          FROM TELEFONE t
         WHERE t.CPF = c.CPF)                                       AS Telefones,
       c.Fidelidade_Ativa,
       c.Data_Primeira_Visita,
       COUNT(DISTINCT a.ID_Atendimento)                             AS Visitas,
       CAST(MAX(a.Data_Hora_Abertura) AS DATE)                      AS Ultima_Visita,
       COALESCE(SUM(pe.Quantidade), 0)                              AS Itens,
       COALESCE(SUM(pe.Quantidade * pr.Preco_Atual), 0)             AS Total_Gasto,
       ROUND(SUM(pe.Quantidade * pr.Preco_Atual)
             / NULLIF(COUNT(DISTINCT a.ID_Atendimento), 0), 2)      AS Ticket_Medio,
       (SELECT pr2.Categoria
          FROM ATENDIMENTO a2
          JOIN PEDIDO pe2  ON pe2.ID_Atendimento = a2.ID_Atendimento
          JOIN PRODUTO pr2 ON pr2.Codigo_Prod = pe2.Codigo_Prod
         WHERE a2.CPF_Cliente = c.CPF
         GROUP BY pr2.Categoria
         ORDER BY SUM(pe2.Quantidade * pr2.Preco_Atual) DESC, pr2.Categoria
         LIMIT 1)                                                   AS Categoria_Favorita,
       CASE
           WHEN COUNT(DISTINCT a.ID_Atendimento) = 0 THEN 'SEM CONSUMO'
           WHEN COUNT(DISTINCT a.ID_Atendimento) >= 3 THEN 'FREQUENTE'
           WHEN COUNT(DISTINCT a.ID_Atendimento) = 2 THEN 'RECORRENTE'
           ELSE 'OCASIONAL'
       END                                                          AS Segmento,
       CASE
           WHEN NOT c.Fidelidade_Ativa
                AND (COUNT(DISTINCT a.ID_Atendimento) >= 2
                     OR COALESCE(SUM(pe.Quantidade * pr.Preco_Atual), 0) >= 300)
               THEN 'Oferecer fidelidade'
           WHEN c.Fidelidade_Ativa
                AND COUNT(DISTINCT a.ID_Atendimento) <= 1
                AND COALESCE(SUM(pe.Quantidade * pr.Preco_Atual), 0) < 100
               THEN 'Rever fidelidade'
           ELSE '-'
       END                                                          AS Sugestao
  FROM CLIENTE c
  JOIN PESSOA p            ON p.CPF = c.CPF
  LEFT JOIN ATENDIMENTO a  ON a.CPF_Cliente = c.CPF
  LEFT JOIN PEDIDO pe      ON pe.ID_Atendimento = a.ID_Atendimento
  LEFT JOIN PRODUTO pr     ON pr.Codigo_Prod = pe.Codigo_Prod
 GROUP BY c.CPF, p.Nome, c.Fidelidade_Ativa, c.Data_Primeira_Visita;

SELECT table_name AS visao
  FROM information_schema.views
 WHERE table_schema = 'public'
 ORDER BY table_name;
