package br.cesar.bar.repository;

import br.cesar.bar.model.Consulta;
import br.cesar.bar.model.ResultadoTabela;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Consultas das Etapas 03 e 04. O mesmo SQL está em sql/consultas/consultas.sql e
 * sql/consultas/etapa04_consultas.sql (entregáveis). As da Etapa 04 usam as visões de
 * sql/11_visoes.sql. O valor de um item é Quantidade * Preco_Atual, já que PEDIDO não guarda
 * o preço cobrado.
 */
@Repository
public class ConsultaRepository {

    private static final List<Consulta> CONSULTAS = List.of(
            new Consulta(
                    "faturamento-categoria", "03",
                    "Faturamento por categoria de produto",
                    "Quanto cada categoria do cardápio faturou, quantos itens vendeu, em quantos "
                            + "atendimentos apareceu e qual a sua participação no faturamento total.",
                    List.of("JOIN", "GROUP BY", "SUM / COUNT DISTINCT", "Função de janela (SUM OVER)"),
                    """
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
                     ORDER BY "Faturamento (R$)" DESC
                    """),
            new Consulta(
                    "ranking-atendentes", "03",
                    "Ranking de atendentes por faturamento",
                    "Funcionários que atenderam mesas, ordenados pelo valor consumido nos seus "
                            + "atendimentos, com ticket médio e o nome do supervisor de cada um.",
                    List.of("JOIN de 6 tabelas", "LEFT JOIN (autorrelacionamento)", "GROUP BY",
                            "Função de janela (RANK)"),
                    """
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
                     ORDER BY "Posicao", "Funcionario"
                    """),
            new Consulta(
                    "conferencia-contas", "03",
                    "Conferência de contas: consumido x pago",
                    "Para cada atendimento, compara o total consumido (PEDIDO x PRODUTO) com a soma "
                            + "dos pagamentos parciais e classifica a conta. As divergências aparecem primeiro.",
                    List.of("CTE (WITH)", "Subconsultas agregadas", "LEFT JOIN", "CASE", "COALESCE"),
                    """
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
                              a.ID_Atendimento
                    """),
            new Consulta(
                    "clientes-acima-media", "03",
                    "Clientes que gastam acima da média",
                    "Clientes cujo gasto total supera a média de gasto de todos os clientes, com o "
                            + "número de visitas, o gasto por visita e o produto que mais pediram.",
                    List.of("CTE (WITH)", "Subconsulta no WHERE (AVG)", "Subconsulta correlacionada",
                            "JOIN", "LIMIT"),
                    """
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
                     ORDER BY g.total DESC
                    """),
            new Consulta(
                    "hierarquia-supervisao", "03",
                    "Hierarquia de supervisão e folha salarial",
                    "Cada gerente com a quantidade de subordinados, os cargos que supervisiona e o "
                            + "custo da equipe, a partir do autorrelacionamento de FUNCIONARIO.",
                    List.of("Autojunção (self-join)", "JOIN", "GROUP BY", "STRING_AGG", "SUM / AVG"),
                    """
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
                     ORDER BY "Subordinados" DESC, "Supervisor"
                    """),
            new Consulta(
                    "mesas-ociosas", "04",
                    "Mesas ociosas (anti join)",
                    "Mesas que nunca receberam atendimento no período: o LEFT JOIN mantém todas as "
                            + "mesas e o filtro IS NULL deixa só as que não têm par em ATENDIMENTO.",
                    List.of("Anti join (LEFT JOIN + IS NULL)", "Função de janela (COUNT OVER)",
                            "Subconsulta escalar", "Índice idx_atendimento_mesa"),
                    """
                    SELECT m.Numero_Mesa                                        AS "Mesa",
                           m.Localizacao                                        AS "Area",
                           m.Capacidade                                         AS "Lugares",
                           COUNT(*) OVER (PARTITION BY m.Localizacao)           AS "Mesas ociosas na area",
                           (SELECT COUNT(*) FROM MESA m2
                             WHERE m2.Localizacao = m.Localizacao)              AS "Total de mesas na area"
                      FROM MESA m
                      LEFT JOIN ATENDIMENTO a ON a.Numero_Mesa = m.Numero_Mesa
                     WHERE a.ID_Atendimento IS NULL
                     ORDER BY m.Localizacao, m.Numero_Mesa
                    """),
            new Consulta(
                    "sabado-domingo", "04",
                    "Vendas por produto: sábado x domingo (full outer join)",
                    "Compara as duas noites de maior movimento (19/09 e 20/09). O FULL OUTER JOIN mantém "
                            + "os produtos vendidos nos dois dias e também os vendidos em só um deles.",
                    List.of("FULL OUTER JOIN", "CTE (WITH)", "COALESCE", "CASE",
                            "Visão vw_itens_vendidos"),
                    """
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
                     ORDER BY "Situacao" DESC, ABS(COALESCE(d.valor, 0) - COALESCE(s.valor, 0)) DESC, "Produto"
                    """),
            new Consulta(
                    "todas-categorias", "04",
                    "Clientes que pediram de todas as categorias (subconsulta)",
                    "Divisão relacional: clientes para os quais NÃO EXISTE categoria do cardápio que "
                            + "eles NÃO tenham pedido. Público ideal para divulgar novidades.",
                    List.of("NOT EXISTS duplo (divisão relacional)", "Subconsultas escalares",
                            "Subconsulta no FROM", "Visão vw_itens_vendidos"),
                    """
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
                     ORDER BY "Total gasto (R$)" DESC
                    """),
            new Consulta(
                    "acima-media-categoria", "04",
                    "Produtos mais caros que a média da categoria (subconsulta)",
                    "A subconsulta correlacionada do WHERE calcula a média de preço da categoria de cada "
                            + "produto; as do SELECT comparam o que ele vende com a média da categoria.",
                    List.of("Subconsulta correlacionada no WHERE", "Subconsultas no SELECT", "AVG",
                            "Índice idx_pedido_produto"),
                    """
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
                     ORDER BY pr.Categoria, pr.Preco_Atual DESC
                    """)
    );

    private final JdbcTemplate jdbc;

    public ConsultaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Consulta> listar() {
        return CONSULTAS;
    }

    public Optional<Consulta> buscar(String id) {
        return CONSULTAS.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public ResultadoTabela executar(Consulta consulta) {
        return jdbc.query(consulta.sql(), Resultados.EXTRATOR);
    }
}
