package br.cesar.bar.repository;

import br.cesar.bar.model.Consulta;
import br.cesar.bar.model.Grafico;
import br.cesar.bar.model.Parametro;
import br.cesar.bar.model.ResultadoTabela;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas das Etapas 03 e 04 e as visões de sql/11_visoes.sql, agora com filtros.
 * A versão sem filtros de cada consulta está em sql/consultas/consultas.sql e
 * sql/consultas/etapa04_consultas.sql. Aqui cada filtro vira um parâmetro nomeado: quando o
 * campo fica vazio o parâmetro vai NULL e a condição (CAST(:p AS ...) IS NULL OR ...) deixa
 * de filtrar. O valor de um item é Quantidade * Preco_Atual, já que PEDIDO não guarda o preço.
 */
@Repository
public class ConsultaRepository {

    private static final String AREAS = "SELECT DISTINCT Localizacao FROM MESA ORDER BY 1";
    private static final String CATEGORIAS = "SELECT DISTINCT Categoria FROM PRODUTO ORDER BY 1";
    private static final List<String> SIM_NAO = List.of("Sim", "Nao");

    private static final Parametro INICIO = Parametro.data("inicio", "De", "");
    private static final Parametro FIM = Parametro.data("fim", "Até", "");

    /** Filtro de período sobre a data de abertura do atendimento (alias a). */
    private static final String PERIODO = """
            (CAST(:inicio AS DATE) IS NULL OR CAST(a.Data_Hora_Abertura AS DATE) >= CAST(:inicio AS DATE))
                       AND (CAST(:fim AS DATE) IS NULL OR CAST(a.Data_Hora_Abertura AS DATE) <= CAST(:fim AS DATE))""";

    private static final List<Consulta> CONSULTAS = List.of(
            new Consulta(
                    "faturamento-categoria", "03",
                    "Faturamento por categoria de produto",
                    "Quanto cada categoria do cardápio faturou, quantos itens vendeu, em quantos "
                            + "atendimentos apareceu e qual a sua participação no faturamento do período.",
                    List.of("JOIN", "GROUP BY", "SUM / COUNT DISTINCT", "Função de janela (SUM OVER)"),
                    List.of(INICIO, FIM, Parametro.listaDoBanco("area", "Área do bar", AREAS)),
                    Grafico.linhas("doughnut", "Categoria", "Participação no faturamento", "Faturamento (R$)"),
                    """
                    SELECT pr.Categoria                                AS "Categoria",
                           COUNT(DISTINCT pe.ID_Atendimento)           AS "Atendimentos",
                           SUM(pe.Quantidade)                          AS "Itens vendidos",
                           SUM(pe.Quantidade * pr.Preco_Atual)         AS "Faturamento (R$)",
                           ROUND(100.0 * SUM(pe.Quantidade * pr.Preco_Atual)
                                 / SUM(SUM(pe.Quantidade * pr.Preco_Atual)) OVER (), 1)
                                                                       AS "Participacao (%)"
                      FROM PEDIDO pe
                      JOIN PRODUTO pr     ON pr.Codigo_Prod = pe.Codigo_Prod
                      JOIN ATENDIMENTO a  ON a.ID_Atendimento = pe.ID_Atendimento
                      JOIN MESA m         ON m.Numero_Mesa = a.Numero_Mesa
                     WHERE {periodo}
                       AND (CAST(:area AS VARCHAR) IS NULL OR m.Localizacao = :area)
                     GROUP BY pr.Categoria
                     ORDER BY "Faturamento (R$)" DESC
                    """.replace("{periodo}", PERIODO)),
            new Consulta(
                    "ranking-atendentes", "03",
                    "Ranking de atendentes por faturamento",
                    "Funcionários que atenderam mesas, ordenados pelo valor consumido nos seus "
                            + "atendimentos, com ticket médio e o nome do supervisor de cada um.",
                    List.of("JOIN de 6 tabelas", "LEFT JOIN (autorrelacionamento)", "GROUP BY",
                            "Função de janela (RANK)"),
                    List.of(INICIO, FIM, Parametro.listaDoBanco("cargo", "Cargo",
                            "SELECT DISTINCT f.Cargo FROM FUNCIONARIO f JOIN ATENDIMENTO a ON a.CPF_Garcom = f.CPF ORDER BY 1")),
                    Grafico.linhas("barh", "Funcionario", "Faturamento por atendente", "Faturamento (R$)"),
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
                     WHERE {periodo}
                       AND (CAST(:cargo AS VARCHAR) IS NULL OR f.Cargo = :cargo)
                     GROUP BY f.CPF, p.Nome, f.Cargo, ps.Nome
                     ORDER BY "Posicao", "Funcionario"
                    """.replace("{periodo}", PERIODO)),
            new Consulta(
                    "conferencia-contas", "03",
                    "Conferência de contas: consumido x pago",
                    "Para cada atendimento, compara o total consumido (PEDIDO x PRODUTO) com a soma "
                            + "dos pagamentos parciais e classifica a conta. As divergências aparecem primeiro.",
                    List.of("CTE (WITH)", "Subconsultas agregadas", "LEFT JOIN", "CASE", "COALESCE"),
                    List.of(INICIO, FIM,
                            Parametro.lista("status", "Status da conta", List.of("ABERTA", "FECHADA")),
                            Parametro.lista("situacao", "Situação", List.of("QUITADA", "PENDENTE", "PAGO A MAIS"))),
                    Grafico.contagem("doughnut", "Situacao", "Contas por situação"),
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
                    ),
                    conferencia AS (
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
                         WHERE {periodo}
                           AND (CAST(:status AS VARCHAR) IS NULL OR a.Status_Conta = :status)
                    )
                    SELECT *
                      FROM conferencia
                     WHERE CAST(:situacao AS VARCHAR) IS NULL OR "Situacao" = :situacao
                     ORDER BY ABS("Saldo (R$)") DESC, "Atendimento"
                    """.replace("{periodo}", PERIODO)),
            new Consulta(
                    "clientes-acima-media", "03",
                    "Clientes que gastam acima da média",
                    "Clientes cujo gasto total no período supera a média de gasto de todos os clientes, "
                            + "com o número de visitas, o gasto por visita e o produto que mais pediram.",
                    List.of("CTE (WITH)", "Subconsulta no WHERE (AVG)", "Subconsulta correlacionada",
                            "JOIN", "LIMIT"),
                    List.of(INICIO, FIM, Parametro.lista("fidelidade", "Fidelidade ativa", SIM_NAO)),
                    Grafico.linhas("barh", "Cliente", "Gasto total x média", "Total gasto (R$)"),
                    """
                    WITH gasto AS (
                        SELECT a.CPF_Cliente,
                               COUNT(DISTINCT a.ID_Atendimento)    AS visitas,
                               SUM(pe.Quantidade * pr.Preco_Atual) AS total
                          FROM ATENDIMENTO a
                          JOIN PEDIDO pe  ON pe.ID_Atendimento = a.ID_Atendimento
                          JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
                         WHERE {periodo}
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
                       AND (CAST(:fidelidade AS VARCHAR) IS NULL OR c.Fidelidade_Ativa = (:fidelidade = 'Sim'))
                     ORDER BY g.total DESC
                    """.replace("{periodo}", PERIODO)),
            new Consulta(
                    "hierarquia-supervisao", "03",
                    "Hierarquia de supervisão e folha salarial",
                    "Cada gerente com a quantidade de subordinados, os cargos que supervisiona e o "
                            + "custo da equipe, a partir do autorrelacionamento de FUNCIONARIO.",
                    List.of("Autojunção (self-join)", "JOIN", "GROUP BY", "STRING_AGG", "SUM / AVG"),
                    List.of(Parametro.listaDoBanco("cargo", "Cargo do subordinado",
                            "SELECT DISTINCT Cargo FROM FUNCIONARIO WHERE CPF_Supervisor IS NOT NULL ORDER BY 1")),
                    Grafico.linhas("bar", "Supervisor", "Folha da equipe por supervisor", "Folha da equipe (R$)"),
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
                     WHERE CAST(:cargo AS VARCHAR) IS NULL OR f.Cargo = :cargo
                     GROUP BY s.CPF, ps.Nome, s.Matricula, s.Salario
                     ORDER BY "Subordinados" DESC, "Supervisor"
                    """),
            new Consulta(
                    "mesas-ociosas", "04",
                    "Mesas ociosas (anti join)",
                    "Mesas que não receberam atendimento no período escolhido: o LEFT JOIN mantém todas as "
                            + "mesas e o filtro IS NULL deixa só as que não têm par em ATENDIMENTO. O período "
                            + "fica na condição do JOIN para não transformar o LEFT JOIN em INNER JOIN.",
                    List.of("Anti join (LEFT JOIN + IS NULL)", "Função de janela (COUNT OVER)",
                            "Subconsulta escalar", "Índice idx_atendimento_mesa"),
                    List.of(INICIO, FIM, Parametro.listaDoBanco("area", "Área do bar", AREAS)),
                    Grafico.contagem("bar", "Area", "Mesas ociosas por área"),
                    """
                    SELECT m.Numero_Mesa                                        AS "Mesa",
                           m.Localizacao                                        AS "Area",
                           m.Capacidade                                         AS "Lugares",
                           COUNT(*) OVER (PARTITION BY m.Localizacao)           AS "Mesas ociosas na area",
                           (SELECT COUNT(*) FROM MESA m2
                             WHERE m2.Localizacao = m.Localizacao)              AS "Total de mesas na area"
                      FROM MESA m
                      LEFT JOIN ATENDIMENTO a ON a.Numero_Mesa = m.Numero_Mesa
                                             AND {periodo}
                     WHERE a.ID_Atendimento IS NULL
                       AND (CAST(:area AS VARCHAR) IS NULL OR m.Localizacao = :area)
                     ORDER BY m.Localizacao, m.Numero_Mesa
                    """.replace("{periodo}", PERIODO)),
            new Consulta(
                    "comparativo-dias", "04",
                    "Vendas por produto: dia A x dia B (full outer join)",
                    "Compara as vendas de dois dias. O FULL OUTER JOIN mantém os produtos vendidos nos "
                            + "dois dias e também os vendidos em só um deles. O padrão é o fim de semana "
                            + "de 19/09 (sábado) e 20/09 (domingo).",
                    List.of("FULL OUTER JOIN", "CTE (WITH)", "COALESCE", "CASE",
                            "Visão vw_itens_vendidos"),
                    List.of(Parametro.data("diaA", "Dia A", "2026-09-19"),
                            Parametro.data("diaB", "Dia B", "2026-09-20"),
                            Parametro.listaDoBanco("categoria", "Categoria", CATEGORIAS)),
                    Grafico.linhas("bar", "Produto", "Faturamento por produto nos dois dias",
                            "Faturamento dia A (R$)", "Faturamento dia B (R$)"),
                    """
                    WITH dia_a AS (
                        SELECT Codigo_Prod, Nome_Produto, Categoria,
                               SUM(Quantidade) AS qtd,
                               SUM(Valor_Item) AS valor
                          FROM vw_itens_vendidos
                         WHERE Data_Atendimento = CAST(:diaA AS DATE)
                           AND (CAST(:categoria AS VARCHAR) IS NULL OR Categoria = :categoria)
                         GROUP BY Codigo_Prod, Nome_Produto, Categoria
                    ),
                    dia_b AS (
                        SELECT Codigo_Prod, Nome_Produto, Categoria,
                               SUM(Quantidade) AS qtd,
                               SUM(Valor_Item) AS valor
                          FROM vw_itens_vendidos
                         WHERE Data_Atendimento = CAST(:diaB AS DATE)
                           AND (CAST(:categoria AS VARCHAR) IS NULL OR Categoria = :categoria)
                         GROUP BY Codigo_Prod, Nome_Produto, Categoria
                    )
                    SELECT COALESCE(x.Nome_Produto, y.Nome_Produto)          AS "Produto",
                           COALESCE(x.Categoria, y.Categoria)                AS "Categoria",
                           COALESCE(x.qtd, 0)                                AS "Qtd dia A",
                           COALESCE(y.qtd, 0)                                AS "Qtd dia B",
                           COALESCE(x.valor, 0)                              AS "Faturamento dia A (R$)",
                           COALESCE(y.valor, 0)                              AS "Faturamento dia B (R$)",
                           COALESCE(y.valor, 0) - COALESCE(x.valor, 0)       AS "Variacao (R$)",
                           CASE
                               WHEN x.Codigo_Prod IS NULL THEN 'So no dia B'
                               WHEN y.Codigo_Prod IS NULL THEN 'So no dia A'
                               ELSE 'Nos dois dias'
                           END                                               AS "Situacao"
                      FROM dia_a x
                      FULL OUTER JOIN dia_b y ON y.Codigo_Prod = x.Codigo_Prod
                     ORDER BY "Situacao", ABS(COALESCE(y.valor, 0) - COALESCE(x.valor, 0)) DESC, "Produto"
                    """),
            new Consulta(
                    "todas-categorias", "04",
                    "Clientes que pediram de todas as categorias (subconsulta)",
                    "Divisão relacional: clientes para os quais NÃO EXISTE categoria do cardápio que "
                            + "eles NÃO tenham pedido. Público ideal para divulgar novidades.",
                    List.of("NOT EXISTS duplo (divisão relacional)", "Subconsultas escalares",
                            "Subconsulta no FROM", "Visão vw_itens_vendidos"),
                    List.of(Parametro.lista("fidelidade", "Fidelidade ativa", SIM_NAO),
                            Parametro.numero("minVisitas", "Mínimo de visitas", "")),
                    Grafico.linhas("barh", "Cliente", "Gasto total dos clientes", "Total gasto (R$)"),
                    """
                    SELECT *
                      FROM (SELECT p.Nome                                                   AS "Cliente",
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
                               AND (CAST(:fidelidade AS VARCHAR) IS NULL
                                    OR c.Fidelidade_Ativa = (:fidelidade = 'Sim'))) t
                     WHERE CAST(:minVisitas AS INTEGER) IS NULL OR "Visitas" >= CAST(:minVisitas AS INTEGER)
                     ORDER BY "Total gasto (R$)" DESC
                    """),
            new Consulta(
                    "acima-media-categoria", "04",
                    "Produtos mais caros que a média da categoria (subconsulta)",
                    "A subconsulta correlacionada do WHERE calcula a média de preço da categoria de cada "
                            + "produto; as do SELECT comparam o que ele vende com a média da categoria.",
                    List.of("Subconsulta correlacionada no WHERE", "Subconsultas no SELECT", "AVG",
                            "Índice idx_pedido_produto"),
                    List.of(Parametro.listaDoBanco("categoria", "Categoria", CATEGORIAS)),
                    Grafico.linhas("bar", "Produto", "Vendido x média da categoria",
                            "Qtd vendida", "Qtd media vendida na categoria"),
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
                       AND (CAST(:categoria AS VARCHAR) IS NULL OR pr.Categoria = :categoria)
                     ORDER BY pr.Categoria, pr.Preco_Atual DESC
                    """),
            new Consulta(
                    "vw-itens-vendidos", "visoes",
                    "Visão vw_itens_vendidos",
                    "Cada item vendido com o contexto do atendimento: produto, valor (Quantidade x Preço), "
                            + "mesa, área, cliente e garçom. Junta 6 tabelas e centraliza a regra do valor do item. "
                            + "O gráfico soma o valor dos itens filtrados por categoria.",
                    List.of("Visão", "JOIN de 6 tabelas", "Filtros por período, categoria, área e garçom"),
                    // abre no fim de semana de 19 e 20/09 para a página não trazer todos os itens de uma vez
                    List.of(Parametro.data("inicio", "De", "2026-09-19"), Parametro.data("fim", "Até", "2026-09-20"),
                            Parametro.listaDoBanco("categoria", "Categoria", CATEGORIAS),
                            Parametro.listaDoBanco("area", "Área do bar", AREAS),
                            Parametro.listaDoBanco("garcom", "Garçom",
                                    "SELECT DISTINCT Garcom FROM vw_itens_vendidos ORDER BY 1"),
                            Parametro.lista("status", "Status da conta", List.of("ABERTA", "FECHADA"))),
                    Grafico.soma("barh", "Categoria", "Valor vendido por categoria", "Valor (R$)"),
                    """
                    SELECT ID_Atendimento    AS "Atendimento",
                           ID_Pedido         AS "Pedido",
                           Data_Hora_Pedido  AS "Horario",
                           Status_Conta      AS "Status",
                           Nome_Produto      AS "Produto",
                           Categoria         AS "Categoria",
                           Quantidade        AS "Qtd",
                           Preco_Unitario    AS "Preco (R$)",
                           Valor_Item        AS "Valor (R$)",
                           Numero_Mesa       AS "Mesa",
                           Localizacao       AS "Area",
                           Cliente           AS "Cliente",
                           Garcom            AS "Garcom"
                      FROM vw_itens_vendidos
                     WHERE (CAST(:inicio AS DATE) IS NULL OR Data_Atendimento >= CAST(:inicio AS DATE))
                       AND (CAST(:fim AS DATE) IS NULL OR Data_Atendimento <= CAST(:fim AS DATE))
                       AND (CAST(:categoria AS VARCHAR) IS NULL OR Categoria = :categoria)
                       AND (CAST(:area AS VARCHAR) IS NULL OR Localizacao = :area)
                       AND (CAST(:garcom AS VARCHAR) IS NULL OR Garcom = :garcom)
                       AND (CAST(:status AS VARCHAR) IS NULL OR Status_Conta = :status)
                     ORDER BY Data_Hora_Pedido DESC, ID_Atendimento, ID_Pedido, Codigo_Prod
                    """),
            new Consulta(
                    "vw-historico-cliente", "visoes",
                    "Visão vw_historico_cliente",
                    "Ficha do cliente para o programa de fidelidade: visitas, gasto, ticket médio, categoria "
                            + "favorita, segmento e sugestão de oferecer ou rever a fidelidade.",
                    List.of("Visão", "LEFT JOIN", "Subconsultas correlacionadas", "CASE"),
                    List.of(Parametro.lista("segmento", "Segmento",
                                    List.of("FREQUENTE", "RECORRENTE", "OCASIONAL", "SEM CONSUMO")),
                            Parametro.lista("fidelidade", "Fidelidade ativa", SIM_NAO),
                            Parametro.lista("sugestao", "Sugestão",
                                    List.of("Oferecer fidelidade", "Rever fidelidade", "-"))),
                    Grafico.contagem("doughnut", "Segmento", "Clientes por segmento"),
                    """
                    SELECT Nome                 AS "Cliente",
                           Telefones            AS "Telefones",
                           Fidelidade_Ativa     AS "Fidelidade",
                           Data_Primeira_Visita AS "Primeira visita",
                           Visitas              AS "Visitas",
                           Ultima_Visita        AS "Ultima visita",
                           Itens                AS "Itens",
                           Total_Gasto          AS "Total gasto (R$)",
                           Ticket_Medio         AS "Ticket medio (R$)",
                           Categoria_Favorita   AS "Categoria favorita",
                           Segmento             AS "Segmento",
                           Sugestao             AS "Sugestao"
                      FROM vw_historico_cliente
                     WHERE (CAST(:segmento AS VARCHAR) IS NULL OR Segmento = :segmento)
                       AND (CAST(:fidelidade AS VARCHAR) IS NULL OR Fidelidade_Ativa = (:fidelidade = 'Sim'))
                       AND (CAST(:sugestao AS VARCHAR) IS NULL OR Sugestao = :sugestao)
                     ORDER BY "Total gasto (R$)" DESC, "Cliente"
                    """)
    );

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Consulta> listar() {
        return CONSULTAS;
    }

    public Optional<Consulta> buscar(String id) {
        return CONSULTAS.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** Opções de cada filtro do tipo lista (fixas ou lidas do banco). */
    public Map<String, List<String>> opcoes(Consulta consulta) {
        Map<String, List<String>> opcoes = new LinkedHashMap<>();
        for (Parametro p : consulta.parametros()) {
            if (p.sqlOpcoes() != null) {
                opcoes.put(p.nome(), jdbc.getJdbcTemplate().queryForList(p.sqlOpcoes(), String.class));
            } else {
                opcoes.put(p.nome(), p.opcoes());
            }
        }
        return opcoes;
    }

    /** Executa a consulta com os valores dos filtros (vazio = sem filtro). */
    public ResultadoTabela executar(Consulta consulta, Map<String, String> valores) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (Parametro p : consulta.parametros()) {
            String v = valores.getOrDefault(p.nome(), "");
            params.addValue(p.nome(), v.isBlank() ? null : v.trim(), Types.VARCHAR);
        }
        return jdbc.query(consulta.sql(), params, Resultados.EXTRATOR);
    }
}
