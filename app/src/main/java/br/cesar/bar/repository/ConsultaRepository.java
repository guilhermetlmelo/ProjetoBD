package br.cesar.bar.repository;

import br.cesar.bar.model.Consulta;
import br.cesar.bar.model.ResultadoTabela;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Consultas da Etapa 03. O mesmo SQL está em sql/consultas/consultas.sql (entregável).
 * O valor de um item é Quantidade * Preco_Atual, já que PEDIDO não guarda o preço cobrado.
 */
@Repository
public class ConsultaRepository {

    private static final List<Consulta> CONSULTAS = List.of(
            new Consulta(
                    "faturamento-categoria",
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
                    "ranking-atendentes",
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
                    "conferencia-contas",
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
                    "clientes-acima-media",
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
                    "hierarquia-supervisao",
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
