package br.cesar.bar.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Dados do dashboard (gráficos de estatística). Cada método é um SELECT explícito; o resultado
 * vai como JSON para os gráficos da página. Os aliases sem aspas chegam em minúsculas.
 */
@Repository
public class DashboardRepository {

    /** Total consumido por atendimento fechado: base das estatísticas descritivas e do histograma. */
    private static final String TOTAIS_FECHADOS = """
            WITH totais AS (
                SELECT a.ID_Atendimento,
                       SUM(pe.Quantidade * pr.Preco_Atual) AS total
                  FROM ATENDIMENTO a
                  JOIN PEDIDO pe  ON pe.ID_Atendimento = a.ID_Atendimento
                  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
                 WHERE a.Status_Conta = 'FECHADA'
                 GROUP BY a.ID_Atendimento
            )
            """;

    /** Largura das faixas do histograma de valor por atendimento, em reais. */
    public static final int LARGURA_FAIXA = 50;

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> indicadores() {
        return jdbc.queryForMap("""
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
                       (SELECT COUNT(*) FROM CLIENTE WHERE Fidelidade_Ativa)               AS fieis
                """);
    }

    public List<Map<String, Object>> faturamentoPorCategoria() {
        return jdbc.queryForList("""
                SELECT pr.Categoria                        AS rotulo,
                       SUM(pe.Quantidade * pr.Preco_Atual) AS valor
                  FROM PEDIDO pe
                  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
                 GROUP BY pr.Categoria
                 ORDER BY valor DESC
                """);
    }

    public List<Map<String, Object>> faturamentoPorDia() {
        return jdbc.queryForList("""
                SELECT TO_CHAR(CAST(a.Data_Hora_Abertura AS DATE), 'DD/MM') AS rotulo,
                       SUM(pe.Quantidade * pr.Preco_Atual)                  AS valor
                  FROM ATENDIMENTO a
                  JOIN PEDIDO pe  ON pe.ID_Atendimento = a.ID_Atendimento
                  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
                 GROUP BY CAST(a.Data_Hora_Abertura AS DATE)
                 ORDER BY CAST(a.Data_Hora_Abertura AS DATE)
                """);
    }

    public List<Map<String, Object>> itensPorHora() {
        return jdbc.queryForList("""
                SELECT LPAD(CAST(EXTRACT(HOUR FROM Data_Hora_Pedido) AS INTEGER)::TEXT, 2, '0') || 'h' AS rotulo,
                       SUM(Quantidade)                                                              AS valor
                  FROM PEDIDO
                 GROUP BY EXTRACT(HOUR FROM Data_Hora_Pedido)
                 -- a noite do bar passa da meia-noite: ordena começando às 12h (00h vem depois das 23h)
                 ORDER BY MOD(CAST(EXTRACT(HOUR FROM Data_Hora_Pedido) AS INTEGER) + 12, 24)
                """);
    }

    public List<Map<String, Object>> pagamentosPorMetodo() {
        return jdbc.queryForList("""
                SELECT Metodo_Pagamento AS rotulo,
                       SUM(Valor_Pago)  AS valor,
                       COUNT(*)         AS quantidade
                  FROM PAGAMENTO_PARCIAL
                 GROUP BY Metodo_Pagamento
                 ORDER BY valor DESC
                """);
    }

    public List<Map<String, Object>> produtosMaisVendidos() {
        return jdbc.queryForList("""
                SELECT pr.Nome_Produto     AS rotulo,
                       SUM(pe.Quantidade)  AS valor
                  FROM PEDIDO pe
                  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
                 GROUP BY pr.Nome_Produto
                 ORDER BY valor DESC, rotulo
                 LIMIT 10
                """);
    }

    public List<Map<String, Object>> atendimentosPorLocalizacao() {
        return jdbc.queryForList("""
                SELECT m.Localizacao      AS rotulo,
                       COUNT(a.ID_Atendimento) AS valor
                  FROM MESA m
                  LEFT JOIN ATENDIMENTO a ON a.Numero_Mesa = m.Numero_Mesa
                 GROUP BY m.Localizacao
                 ORDER BY valor DESC
                """);
    }

    /** Medidas de posição e dispersão do valor consumido por atendimento fechado. */
    public Map<String, Object> estatisticasDescritivas() {
        return jdbc.queryForMap(TOTAIS_FECHADOS + """
                SELECT COUNT(*)                                                     AS n,
                       ROUND(AVG(total), 2)                                         AS media,
                       ROUND(CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS mediana,
                       ROUND(STDDEV_SAMP(total), 2)                                 AS desvio_padrao,
                       ROUND(100 * STDDEV_SAMP(total) / NULLIF(AVG(total), 0), 1)   AS coef_variacao,
                       MIN(total)                                                   AS minimo,
                       ROUND(CAST(PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS q1,
                       ROUND(CAST(PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS q3,
                       MAX(total)                                                   AS maximo
                  FROM totais
                """);
    }

    /** Tabela de frequências (absoluta, relativa e acumulada) em faixas de LARGURA_FAIXA reais. */
    public List<Map<String, Object>> distribuicaoValorAtendimento() {
        return jdbc.queryForList(TOTAIS_FECHADOS + """
                SELECT 'R$ ' || (g.faixa * ?) || ' a ' || ((g.faixa + 1) * ?)          AS rotulo,
                       COUNT(t.total)                                                  AS valor,
                       ROUND(100.0 * COUNT(t.total) / SUM(COUNT(t.total)) OVER (), 1)  AS relativa,
                       SUM(COUNT(t.total)) OVER (ORDER BY g.faixa)                     AS acumulada
                  FROM GENERATE_SERIES(0, (SELECT CAST(FLOOR(MAX(total) / ?) AS INTEGER) FROM totais)) AS g(faixa)
                  LEFT JOIN totais t ON CAST(FLOOR(t.total / ?) AS INTEGER) = g.faixa
                 GROUP BY g.faixa
                 ORDER BY g.faixa
                """, LARGURA_FAIXA, LARGURA_FAIXA, LARGURA_FAIXA, LARGURA_FAIXA);
    }

    /**
     * Tempo de permanência (abertura da conta até o último pagamento, em minutos) e valor
     * consumido de cada atendimento fechado: base da correlação de Pearson.
     */
    private static final String PERMANENCIA_VALOR = """
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
            """;

    /** Pontos do gráfico de dispersão: x = minutos de permanência, y = valor consumido. */
    public List<Map<String, Object>> permanenciaValorPontos() {
        return jdbc.queryForList(PERMANENCIA_VALOR + """
                SELECT ID_Atendimento AS id,
                       minutos        AS x,
                       total          AS y
                  FROM base
                 ORDER BY minutos
                """);
    }

    /**
     * Correlação de Pearson (CORR), coeficiente de determinação, reta de mínimos quadrados
     * (REGR_SLOPE / REGR_INTERCEPT) e estatística t = r * sqrt(n - 2) / sqrt(1 - r²).
     */
    public Map<String, Object> permanenciaValorCorrelacao() {
        return jdbc.queryForMap(PERMANENCIA_VALOR + """
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
                  FROM base
                """);
    }
}
