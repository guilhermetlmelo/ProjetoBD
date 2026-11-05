-- =====================================================================
-- Projeto BD - Bar | Etapa 04: Consultas avancadas (PostgreSQL 16)
-- Requer sql/10_indices.sql e sql/11_visoes.sql (executados pelo
-- scripts/rodar_tudo.ps1).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Consulta 1 (ANTI JOIN a esquerda): mesas ociosas
-- Mesas que nunca receberam um atendimento no periodo. O LEFT JOIN mantem
-- todas as mesas e o filtro "a.ID_Atendimento IS NULL" deixa so as que nao
-- encontraram par em ATENDIMENTO. Ajuda a decidir se vale reduzir mesas ou
-- remanejar garcons de uma area.
-- Indice usado: idx_atendimento_mesa.
-- ---------------------------------------------------------------------
SELECT m.Numero_Mesa                                        AS "Mesa",
       m.Localizacao                                        AS "Area",
       m.Capacidade                                         AS "Lugares",
       COUNT(*) OVER (PARTITION BY m.Localizacao)           AS "Mesas ociosas na area",
       (SELECT COUNT(*) FROM MESA m2
         WHERE m2.Localizacao = m.Localizacao)              AS "Total de mesas na area"
  FROM MESA m
  LEFT JOIN ATENDIMENTO a ON a.Numero_Mesa = m.Numero_Mesa
 WHERE a.ID_Atendimento IS NULL
 ORDER BY m.Localizacao, m.Numero_Mesa;

-- ---------------------------------------------------------------------
-- Consulta 2 (FULL OUTER JOIN): vendas por produto, sabado 19/09 x domingo 20/09
-- Compara as duas noites de maior movimento. O FULL OUTER JOIN mantem os
-- produtos vendidos nos dois dias e tambem os vendidos em apenas um deles
-- (o lado sem venda vem NULL e e tratado com COALESCE). Mostra o que so
-- sai no sabado e o que so sai no domingo.
-- Usa a visao vw_itens_vendidos (indices idx_pedido_atendimento e
-- idx_pedido_produto).
-- ---------------------------------------------------------------------
WITH sabado AS (
    SELECT Codigo_Prod, Nome_Produto, Categoria,
           SUM(Quantidade) AS qtd,
           SUM(Valor_Item) AS valor
      FROM vw_itens_vendidos
     WHERE Data_Atendimento = DATE '2026-09-19'
     GROUP BY Codigo_Prod, Nome_Produto, Categoria
),
domingo AS (
    SELECT Codigo_Prod, Nome_Produto, Categoria,
           SUM(Quantidade) AS qtd,
           SUM(Valor_Item) AS valor
      FROM vw_itens_vendidos
     WHERE Data_Atendimento = DATE '2026-09-20'
     GROUP BY Codigo_Prod, Nome_Produto, Categoria
)
SELECT COALESCE(s.Nome_Produto, d.Nome_Produto)          AS "Produto",
       COALESCE(s.Categoria, d.Categoria)                AS "Categoria",
       COALESCE(s.qtd, 0)                                AS "Qtd sabado",
       COALESCE(d.qtd, 0)                                AS "Qtd domingo",
       COALESCE(s.valor, 0)                              AS "Faturamento sabado (R$)",
       COALESCE(d.valor, 0)                              AS "Faturamento domingo (R$)",
       COALESCE(d.valor, 0) - COALESCE(s.valor, 0)       AS "Variacao (R$)",
       CASE
           WHEN s.Codigo_Prod IS NULL THEN 'So no domingo'
           WHEN d.Codigo_Prod IS NULL THEN 'So no sabado'
           ELSE 'Nos dois dias'
       END                                               AS "Situacao"
  FROM sabado s
  FULL OUTER JOIN domingo d ON d.Codigo_Prod = s.Codigo_Prod
 ORDER BY "Situacao" DESC, ABS(COALESCE(d.valor, 0) - COALESCE(s.valor, 0)) DESC, "Produto";

-- ---------------------------------------------------------------------
-- Consulta 3 (SUBCONSULTA - divisao relacional com NOT EXISTS duplo):
-- clientes que ja pediram produtos de TODAS as categorias do cardapio
-- Leitura: "clientes para os quais NAO EXISTE categoria que eles NAO
-- tenham pedido". Sao os clientes que exploram o cardapio inteiro, publico
-- ideal para divulgar novidades.
-- Indices usados: idx_atendimento_cliente, idx_pedido_atendimento.
-- ---------------------------------------------------------------------
SELECT p.Nome                                                   AS "Cliente",
       CASE WHEN c.Fidelidade_Ativa THEN 'Sim' ELSE 'Nao' END   AS "Fidelidade",
       (SELECT COUNT(*) FROM ATENDIMENTO a
         WHERE a.CPF_Cliente = c.CPF)                           AS "Visitas",
       (SELECT SUM(v.Valor_Item) FROM vw_itens_vendidos v
         WHERE v.CPF_Cliente = c.CPF)                           AS "Total gasto (R$)"
  FROM CLIENTE c
  JOIN PESSOA p ON p.CPF = c.CPF
 WHERE NOT EXISTS (
           SELECT 1
             FROM (SELECT DISTINCT Categoria FROM PRODUTO) cat
            WHERE NOT EXISTS (
                      SELECT 1
                        FROM vw_itens_vendidos v
                       WHERE v.CPF_Cliente = c.CPF
                         AND v.Categoria = cat.Categoria))
 ORDER BY "Total gasto (R$)" DESC;

-- ---------------------------------------------------------------------
-- Consulta 4 (SUBCONSULTAS correlacionadas no WHERE e no SELECT):
-- produtos mais caros que a media da propria categoria, e quanto vendem
-- Para cada produto, a subconsulta do WHERE calcula a media de preco da
-- SUA categoria. As subconsultas do SELECT trazem a quantidade vendida do
-- produto e a media vendida pelos produtos da categoria. Mostra se os itens
-- "premium" de cada categoria vendem mais ou menos que os demais.
-- Indice usado: idx_pedido_produto (soma de vendas por Codigo_Prod).
-- ---------------------------------------------------------------------
SELECT pr.Categoria                                                AS "Categoria",
       pr.Nome_Produto                                             AS "Produto",
       pr.Preco_Atual                                              AS "Preco (R$)",
       (SELECT ROUND(AVG(p2.Preco_Atual), 2)
          FROM PRODUTO p2
         WHERE p2.Categoria = pr.Categoria)                        AS "Preco medio da categoria (R$)",
       (SELECT COALESCE(SUM(pe.Quantidade), 0)
          FROM PEDIDO pe
         WHERE pe.Codigo_Prod = pr.Codigo_Prod)                    AS "Qtd vendida",
       (SELECT ROUND(SUM(pe.Quantidade)::NUMERIC
                     / COUNT(DISTINCT p3.Codigo_Prod), 1)
          FROM PRODUTO p3
          LEFT JOIN PEDIDO pe ON pe.Codigo_Prod = p3.Codigo_Prod
         WHERE p3.Categoria = pr.Categoria)                        AS "Qtd media vendida na categoria"
  FROM PRODUTO pr
 WHERE pr.Preco_Atual > (SELECT AVG(p2.Preco_Atual)
                           FROM PRODUTO p2
                          WHERE p2.Categoria = pr.Categoria)
 ORDER BY pr.Categoria, pr.Preco_Atual DESC;

-- ---------------------------------------------------------------------
-- Visoes (sql/11_visoes.sql) em uso
-- ---------------------------------------------------------------------
SELECT * FROM vw_itens_vendidos ORDER BY ID_Atendimento, ID_Pedido LIMIT 20;

SELECT Nome, Fidelidade_Ativa, Visitas, Ultima_Visita, Total_Gasto, Ticket_Medio,
       Categoria_Favorita, Segmento, Sugestao
  FROM vw_historico_cliente
 ORDER BY Total_Gasto DESC;
