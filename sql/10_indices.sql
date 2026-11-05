-- =====================================================================
-- Projeto BD - Bar | Etapa 04: Indices
-- O PostgreSQL cria indice automaticamente apenas para PRIMARY KEY e UNIQUE.
-- Colunas de FOREIGN KEY NAO ganham indice: todo JOIN por elas faz leitura
-- sequencial da tabela inteira. Os indices abaixo cobrem as FKs usadas nas
-- consultas e visoes da Etapa 04 (sql/consultas/etapa04_consultas.sql e
-- sql/11_visoes.sql).
-- =====================================================================

-- 1) PEDIDO(ID_Atendimento)
-- PEDIDO e a maior tabela do banco e e ligada a ATENDIMENTO em quase todas as
-- consultas (valor da conta, vendas por dia, por cliente, por garcom).
-- O PK de PEDIDO e (ID_Pedido, ID_Atendimento, Codigo_Prod): como comeca por
-- ID_Pedido, ele NAO serve para buscar os itens de um atendimento.
-- Usado em: vw_itens_vendidos, vw_historico_cliente, consultas 2 e 3.
CREATE INDEX idx_pedido_atendimento ON PEDIDO (ID_Atendimento);

-- 2) PEDIDO(Codigo_Prod)
-- Liga cada item ao PRODUTO e responde "quanto foi vendido deste produto?".
-- Tambem acelera a verificacao da FK ao excluir um produto (o PostgreSQL
-- precisa procurar pedidos que o referenciam).
-- Usado em: vw_itens_vendidos, vw_historico_cliente, consulta 4 (subconsulta
-- correlacionada de quantidade vendida por produto).
CREATE INDEX idx_pedido_produto ON PEDIDO (Codigo_Prod);

-- 3) ATENDIMENTO(CPF_Cliente)
-- Historico de um cliente = todos os atendimentos com o seu CPF.
-- Usado em: vw_historico_cliente, consulta 3 (divisao relacional por cliente).
CREATE INDEX idx_atendimento_cliente ON ATENDIMENTO (CPF_Cliente);

-- 4) ATENDIMENTO(Numero_Mesa)
-- Responde "esta mesa ja foi usada?" sem varrer ATENDIMENTO.
-- Usado em: vw_itens_vendidos, consulta 1 (anti join de mesas ociosas).
CREATE INDEX idx_atendimento_mesa ON ATENDIMENTO (Numero_Mesa);

-- ---------------------------------------------------------------------
-- Como verificar o uso dos indices (rodar manualmente no psql):
-- Com poucas linhas, o otimizador prefere leitura sequencial por ser mais
-- barata. Para ver o plano com indice, desligue o seq scan na sessao:
--
--   SET enable_seqscan = off;
--   EXPLAIN SELECT * FROM vw_itens_vendidos WHERE ID_Atendimento = 10;
--   EXPLAIN SELECT m.Numero_Mesa FROM MESA m
--             LEFT JOIN ATENDIMENTO a ON a.Numero_Mesa = m.Numero_Mesa
--            WHERE a.ID_Atendimento IS NULL;
--   RESET enable_seqscan;
-- ---------------------------------------------------------------------

SELECT indexname, indexdef
  FROM pg_indexes
 WHERE schemaname = 'public' AND indexname LIKE 'idx_%'
 ORDER BY indexname;
