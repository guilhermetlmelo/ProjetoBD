-- =====================================================================
-- Projeto BD - Bar | Etapa 03: SQL das consultas (PostgreSQL 16)
-- Gerado a partir de app/src/main/java/br/cesar/bar/repository/.
-- Valor de um item = PEDIDO.Quantidade * PRODUTO.Preco_Atual.
-- =====================================================================

-- ---------------------------------------------------------------------
-- PARTE 1 - CONSULTAS (tela "Consultas" da aplicacao)
-- ---------------------------------------------------------------------

-- Consulta 1: Faturamento por categoria de produto
SELECT pr.Categoria                                AS "Categoria",
       COUNT(DISTINCT pe.ID_Atendimento)           AS "Atendimentos",
       SUM(pe.Quantidade)                          AS "Itens vendidos",
       SUM(pe.Quantidade * pr.Preco_Atual)         AS "Faturamento (R$)",
       ROUND(100.0 * SUM(pe.Quantidade * pr.Preco_Atual)
             / SUM(SUM(pe.Quantidade * pr.Preco_Atual)) OVER (), 1)
                                                   AS "Participacao (%)"
  FROM PEDIDO pe
  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
 GROUP BY pr.Categoria
 ORDER BY "Faturamento (R$)" DESC;

-- Consulta 2: Ranking de atendentes por faturamento
SELECT RANK() OVER (ORDER BY SUM(pe.Quantidade * pr.Preco_Atual) DESC) AS "Posicao",
       p.Nome                                         AS "Funcionario",
       f.Cargo                                        AS "Cargo",
       COALESCE(ps.Nome, '-')                         AS "Supervisor",
       COUNT(DISTINCT a.ID_Atendimento)               AS "Atendimentos",
       SUM(pe.Quantidade * pr.Preco_Atual)            AS "Faturamento (R$)",
       ROUND(SUM(pe.Quantidade * pr.Preco_Atual)
             / COUNT(DISTINCT a.ID_Atendimento), 2)   AS "Ticket medio (R$)"
  FROM FUNCIONARIO f
  JOIN PESSOA p        ON p.CPF = f.CPF
  LEFT JOIN PESSOA ps  ON ps.CPF = f.CPF_Supervisor
  JOIN ATENDIMENTO a   ON a.CPF_Garcom = f.CPF
  JOIN PEDIDO pe       ON pe.ID_Atendimento = a.ID_Atendimento
  JOIN PRODUTO pr      ON pr.Codigo_Prod = pe.Codigo_Prod
 GROUP BY f.CPF, p.Nome, f.Cargo, ps.Nome
 ORDER BY "Posicao", "Funcionario";

-- Consulta 3: Conferência de contas: consumido x pago
WITH consumo AS (
    SELECT pe.ID_Atendimento,
           SUM(pe.Quantidade * pr.Preco_Atual) AS total
      FROM PEDIDO pe
      JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
     GROUP BY pe.ID_Atendimento
),
pagamentos AS (
    SELECT ID_Atendimento,
           SUM(Valor_Pago) AS total,
           COUNT(*)        AS qtd
      FROM PAGAMENTO_PARCIAL
     GROUP BY ID_Atendimento
)
SELECT a.ID_Atendimento                              AS "Atendimento",
       a.Data_Hora_Abertura                          AS "Abertura",
       a.Status_Conta                                AS "Status",
       pc.Nome                                       AS "Cliente",
       a.Numero_Mesa                                 AS "Mesa",
       COALESCE(c.total, 0)                          AS "Consumido (R$)",
       COALESCE(pg.total, 0)                         AS "Pago (R$)",
       COALESCE(pg.qtd, 0)                           AS "Pagamentos",
       COALESCE(c.total, 0) - COALESCE(pg.total, 0)  AS "Saldo (R$)",
       CASE
           WHEN COALESCE(c.total, 0) = COALESCE(pg.total, 0) THEN 'QUITADA'
           WHEN COALESCE(c.total, 0) > COALESCE(pg.total, 0) THEN 'PENDENTE'
           ELSE 'PAGO A MAIS'
       END                                           AS "Situacao"
  FROM ATENDIMENTO a
  JOIN PESSOA pc          ON pc.CPF = a.CPF_Cliente
  LEFT JOIN consumo c     ON c.ID_Atendimento = a.ID_Atendimento
  LEFT JOIN pagamentos pg ON pg.ID_Atendimento = a.ID_Atendimento
 ORDER BY ABS(COALESCE(c.total, 0) - COALESCE(pg.total, 0)) DESC,
          a.ID_Atendimento;

-- Consulta 4: Clientes que gastam acima da média
WITH gasto AS (
    SELECT a.CPF_Cliente,
           COUNT(DISTINCT a.ID_Atendimento)    AS visitas,
           SUM(pe.Quantidade * pr.Preco_Atual) AS total
      FROM ATENDIMENTO a
      JOIN PEDIDO pe  ON pe.ID_Atendimento = a.ID_Atendimento
      JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
     GROUP BY a.CPF_Cliente
)
SELECT p.Nome                                                AS "Cliente",
       CASE WHEN c.Fidelidade_Ativa THEN 'Sim' ELSE 'Nao' END AS "Fidelidade",
       g.visitas                                             AS "Visitas",
       g.total                                               AS "Total gasto (R$)",
       ROUND(g.total / g.visitas, 2)                         AS "Gasto por visita (R$)",
       ROUND(g.total - (SELECT AVG(total) FROM gasto), 2)    AS "Acima da media (R$)",
       (SELECT pr2.Nome_Produto
          FROM ATENDIMENTO a2
          JOIN PEDIDO pe2  ON pe2.ID_Atendimento = a2.ID_Atendimento
          JOIN PRODUTO pr2 ON pr2.Codigo_Prod = pe2.Codigo_Prod
         WHERE a2.CPF_Cliente = g.CPF_Cliente
         GROUP BY pr2.Nome_Produto
         ORDER BY SUM(pe2.Quantidade) DESC, pr2.Nome_Produto
         LIMIT 1)                                            AS "Produto favorito"
  FROM gasto g
  JOIN CLIENTE c ON c.CPF = g.CPF_Cliente
  JOIN PESSOA p  ON p.CPF = c.CPF
 WHERE g.total > (SELECT AVG(total) FROM gasto)
 ORDER BY g.total DESC;

-- Consulta 5: Hierarquia de supervisão e folha salarial
SELECT ps.Nome                                           AS "Supervisor",
       s.Matricula                                       AS "Matricula",
       s.Salario                                         AS "Salario proprio (R$)",
       COUNT(f.CPF)                                      AS "Subordinados",
       STRING_AGG(DISTINCT f.Cargo, ', ' ORDER BY f.Cargo) AS "Cargos supervisionados",
       SUM(f.Salario)                                    AS "Folha da equipe (R$)",
       ROUND(AVG(f.Salario), 2)                          AS "Salario medio da equipe (R$)"
  FROM FUNCIONARIO s
  JOIN PESSOA ps     ON ps.CPF = s.CPF
  JOIN FUNCIONARIO f ON f.CPF_Supervisor = s.CPF
 GROUP BY s.CPF, ps.Nome, s.Matricula, s.Salario
 ORDER BY "Subordinados" DESC, "Supervisor";


-- ---------------------------------------------------------------------
-- PARTE 2 - CONSULTAS DO DASHBOARD (graficos de estatistica)
-- ---------------------------------------------------------------------

-- Indicadores (cartões do topo)
SELECT (SELECT COALESCE(SUM(pe.Quantidade * pr.Preco_Atual), 0)
          FROM PEDIDO pe JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod) AS faturamento,
       (SELECT COUNT(*) FROM ATENDIMENTO)                                  AS atendimentos,
       (SELECT COUNT(*) FROM ATENDIMENTO WHERE Status_Conta = 'ABERTA')    AS abertos,
       (SELECT ROUND(AVG(t.total), 2)
          FROM (SELECT SUM(pe.Quantidade * pr.Preco_Atual) AS total
                  FROM PEDIDO pe JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
                 GROUP BY pe.ID_Atendimento) t)                            AS ticket_medio,
       (SELECT COALESCE(SUM(Quantidade), 0) FROM PEDIDO)                   AS itens,
       (SELECT COUNT(*) FROM CLIENTE)                                      AS clientes,
       (SELECT COUNT(*) FROM CLIENTE WHERE Fidelidade_Ativa)               AS fieis;

-- Faturamento por categoria
SELECT pr.Categoria                        AS rotulo,
       SUM(pe.Quantidade * pr.Preco_Atual) AS valor
  FROM PEDIDO pe
  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
 GROUP BY pr.Categoria
 ORDER BY valor DESC;

-- Faturamento por dia
SELECT TO_CHAR(CAST(a.Data_Hora_Abertura AS DATE), 'DD/MM') AS rotulo,
       SUM(pe.Quantidade * pr.Preco_Atual)                  AS valor
  FROM ATENDIMENTO a
  JOIN PEDIDO pe  ON pe.ID_Atendimento = a.ID_Atendimento
  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
 GROUP BY CAST(a.Data_Hora_Abertura AS DATE)
 ORDER BY CAST(a.Data_Hora_Abertura AS DATE);

-- Itens pedidos por hora do dia
SELECT LPAD(CAST(EXTRACT(HOUR FROM Data_Hora_Pedido) AS INTEGER)::TEXT, 2, '0') || 'h' AS rotulo,
       SUM(Quantidade)                                                              AS valor
  FROM PEDIDO
 GROUP BY EXTRACT(HOUR FROM Data_Hora_Pedido)
 -- a noite do bar passa da meia-noite: ordena começando às 12h (00h vem depois das 23h)
 ORDER BY MOD(CAST(EXTRACT(HOUR FROM Data_Hora_Pedido) AS INTEGER) + 12, 24);

-- Valor recebido por forma de pagamento
SELECT Metodo_Pagamento AS rotulo,
       SUM(Valor_Pago)  AS valor,
       COUNT(*)         AS quantidade
  FROM PAGAMENTO_PARCIAL
 GROUP BY Metodo_Pagamento
 ORDER BY valor DESC;

-- 10 produtos mais vendidos
SELECT pr.Nome_Produto     AS rotulo,
       SUM(pe.Quantidade)  AS valor
  FROM PEDIDO pe
  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
 GROUP BY pr.Nome_Produto
 ORDER BY valor DESC, rotulo
 LIMIT 10;

-- Atendimentos por area do bar
SELECT m.Localizacao      AS rotulo,
       COUNT(a.ID_Atendimento) AS valor
  FROM MESA m
  LEFT JOIN ATENDIMENTO a ON a.Numero_Mesa = m.Numero_Mesa
 GROUP BY m.Localizacao
 ORDER BY valor DESC;

-- Estatisticas descritivas do valor por atendimento (media, mediana, desvio padrao, quartis)
WITH totais AS (
    SELECT a.ID_Atendimento,
           SUM(pe.Quantidade * pr.Preco_Atual) AS total
      FROM ATENDIMENTO a
      JOIN PEDIDO pe  ON pe.ID_Atendimento = a.ID_Atendimento
      JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
     WHERE a.Status_Conta = 'FECHADA'
     GROUP BY a.ID_Atendimento
)
SELECT COUNT(*)                                                     AS n,
       ROUND(AVG(total), 2)                                         AS media,
       ROUND(CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS mediana,
       ROUND(STDDEV_SAMP(total), 2)                                 AS desvio_padrao,
       ROUND(100 * STDDEV_SAMP(total) / NULLIF(AVG(total), 0), 1)   AS coef_variacao,
       MIN(total)                                                   AS minimo,
       ROUND(CAST(PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS q1,
       ROUND(CAST(PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS q3,
       MAX(total)                                                   AS maximo
  FROM totais;

-- Tabela de frequencias / histograma do valor por atendimento (faixas de R$ 50)
WITH totais AS (
    SELECT a.ID_Atendimento,
           SUM(pe.Quantidade * pr.Preco_Atual) AS total
      FROM ATENDIMENTO a
      JOIN PEDIDO pe  ON pe.ID_Atendimento = a.ID_Atendimento
      JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
     WHERE a.Status_Conta = 'FECHADA'
     GROUP BY a.ID_Atendimento
)
SELECT 'R$ ' || (g.faixa * 50) || ' a ' || ((g.faixa + 1) * 50)          AS rotulo,
       COUNT(t.total)                                                  AS valor,
       ROUND(100.0 * COUNT(t.total) / SUM(COUNT(t.total)) OVER (), 1)  AS relativa,
       SUM(COUNT(t.total)) OVER (ORDER BY g.faixa)                     AS acumulada
  FROM GENERATE_SERIES(0, (SELECT CAST(FLOOR(MAX(total) / 50) AS INTEGER) FROM totais)) AS g(faixa)
  LEFT JOIN totais t ON CAST(FLOOR(t.total / 50) AS INTEGER) = g.faixa
 GROUP BY g.faixa
 ORDER BY g.faixa;


-- ---------------------------------------------------------------------
-- PARTE 3 - CORRELACAO DE PEARSON: tempo de permanencia x valor gasto
-- Tempo = da abertura da conta ate o ultimo pagamento parcial (minutos).
-- O p-valor do teste t e calculado na aplicacao (classe TesteT).
-- ---------------------------------------------------------------------

-- Pontos do grafico de dispersao
WITH permanencia AS (
    SELECT a.ID_Atendimento,
           ROUND(EXTRACT(EPOCH FROM (MAX(pg.Horario_Pagamento) - a.Data_Hora_Abertura)) / 60) AS minutos
      FROM ATENDIMENTO a
      JOIN PAGAMENTO_PARCIAL pg ON pg.ID_Atendimento = a.ID_Atendimento
     WHERE a.Status_Conta = 'FECHADA'
     GROUP BY a.ID_Atendimento, a.Data_Hora_Abertura
),
consumo AS (
    SELECT pe.ID_Atendimento,
           SUM(pe.Quantidade * pr.Preco_Atual) AS total
      FROM PEDIDO pe
      JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
     GROUP BY pe.ID_Atendimento
),
base AS (
    SELECT p.ID_Atendimento, p.minutos, c.total
      FROM permanencia p
      JOIN consumo c ON c.ID_Atendimento = p.ID_Atendimento
)
SELECT ID_Atendimento AS id,
       minutos        AS x,
       total          AS y
  FROM base
 ORDER BY minutos;

-- Coeficiente r, r2, reta de regressao e estatistica t = r * sqrt(n - 2) / sqrt(1 - r2)
WITH permanencia AS (
    SELECT a.ID_Atendimento,
           ROUND(EXTRACT(EPOCH FROM (MAX(pg.Horario_Pagamento) - a.Data_Hora_Abertura)) / 60) AS minutos
      FROM ATENDIMENTO a
      JOIN PAGAMENTO_PARCIAL pg ON pg.ID_Atendimento = a.ID_Atendimento
     WHERE a.Status_Conta = 'FECHADA'
     GROUP BY a.ID_Atendimento, a.Data_Hora_Abertura
),
consumo AS (
    SELECT pe.ID_Atendimento,
           SUM(pe.Quantidade * pr.Preco_Atual) AS total
      FROM PEDIDO pe
      JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
     GROUP BY pe.ID_Atendimento
),
base AS (
    SELECT p.ID_Atendimento, p.minutos, c.total
      FROM permanencia p
      JOIN consumo c ON c.ID_Atendimento = p.ID_Atendimento
)
SELECT COUNT(*)                                               AS n,
       ROUND(CAST(CORR(total, minutos) AS NUMERIC), 3)        AS r,
       ROUND(CAST(POWER(CORR(total, minutos), 2) AS NUMERIC), 3) AS r2,
       ROUND(CAST(REGR_SLOPE(total, minutos) AS NUMERIC), 4)  AS inclinacao,
       ROUND(CAST(REGR_INTERCEPT(total, minutos) AS NUMERIC), 2) AS intercepto,
       ROUND(CAST(CORR(total, minutos) * SQRT(COUNT(*) - 2)
                  / SQRT(1 - POWER(CORR(total, minutos), 2)) AS NUMERIC), 3) AS t,
       COUNT(*) - 2                                           AS gl,
       MIN(minutos)                                           AS x_min,
       MAX(minutos)                                           AS x_max,
       CASE
           WHEN ABS(CORR(total, minutos)) < 0.3 THEN 'fraca'
           WHEN ABS(CORR(total, minutos)) < 0.7 THEN 'moderada'
           ELSE 'forte'
       END || CASE WHEN CORR(total, minutos) >= 0 THEN ' positiva' ELSE ' negativa' END
                                                              AS intensidade
  FROM base;
