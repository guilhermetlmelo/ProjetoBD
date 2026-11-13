package br.cesar.bar.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;

/**
 * Dados do dashboard. Tudo parte da visão vw_itens_vendidos filtrada pelo período (data de
 * abertura da conta), pela categoria do produto e pela área da mesa. A CTE "contas" junta os
 * itens filtrados por atendimento. Os aliases sem aspas chegam em minúsculas no Map.
 */
@Repository
public class DashboardRepository {

    /** Filtros do dashboard. Campo nulo ou vazio = sem filtro. Datas no formato yyyy-MM-dd. */
    public record Filtro(String inicio, String fim, String categoria, String area) {
    }

    /** Largura das faixas do histograma de valor por atendimento, em reais. */
    public static final int LARGURA_FAIXA = 50;

    private static final String BASE = """
            WITH itens AS (
                SELECT v.*
                  FROM vw_itens_vendidos v
                 WHERE (CAST(:inicio AS DATE) IS NULL OR v.Data_Atendimento >= CAST(:inicio AS DATE))
                   AND (CAST(:fim AS DATE) IS NULL OR v.Data_Atendimento <= CAST(:fim AS DATE))
                   AND (CAST(:categoria AS VARCHAR) IS NULL OR v.Categoria = :categoria)
                   AND (CAST(:area AS VARCHAR) IS NULL OR v.Localizacao = :area)
            ),
            contas AS (
                SELECT ID_Atendimento, Data_Atendimento, Status_Conta, Localizacao, CPF_Cliente,
                       SUM(Valor_Item) AS total,
                       SUM(Quantidade) AS itens
                  FROM itens
                 GROUP BY ID_Atendimento, Data_Atendimento, Status_Conta, Localizacao, CPF_Cliente
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource params(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("inicio", vazio(f.inicio()), Types.VARCHAR);
        p.addValue("fim", vazio(f.fim()), Types.VARCHAR);
        p.addValue("categoria", vazio(f.categoria()), Types.VARCHAR);
        p.addValue("area", vazio(f.area()), Types.VARCHAR);
        p.addValue("largura", LARGURA_FAIXA);
        return p;
    }

    private static String vazio(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private List<Map<String, Object>> lista(String sql, Filtro f) {
        return jdbc.queryForList(BASE + sql, params(f));
    }

    private Map<String, Object> mapa(String sql, Filtro f) {
        return jdbc.queryForMap(BASE + sql, params(f));
    }

    // ------------------------------------------------------------------ opções dos filtros

    public Map<String, Object> periodoDisponivel() {
        return jdbc.getJdbcTemplate().queryForMap("""
                SELECT TO_CHAR(MIN(Data_Hora_Abertura), 'YYYY-MM-DD') AS inicio,
                       TO_CHAR(MAX(Data_Hora_Abertura), 'YYYY-MM-DD') AS fim
                  FROM ATENDIMENTO
                """);
    }

    public List<String> categorias() {
        return jdbc.getJdbcTemplate().queryForList("SELECT DISTINCT Categoria FROM PRODUTO ORDER BY 1", String.class);
    }

    public List<String> areas() {
        return jdbc.getJdbcTemplate().queryForList("SELECT DISTINCT Localizacao FROM MESA ORDER BY 1", String.class);
    }

    // ------------------------------------------------------------------ indicadores

    /**
     * Indicadores resumidos. pct_quitadas usa a função fn_situacao_conta (Etapa 05) para
     * conferir, conta a conta, se o que foi pago bate com o consumido.
     */
    public Map<String, Object> indicadores(Filtro f) {
        return mapa("""
                SELECT COALESCE(SUM(ct.total), 0)                                       AS faturamento,
                       COUNT(*)                                                         AS atendimentos,
                       COUNT(*) FILTER (WHERE ct.Status_Conta = 'ABERTA')               AS abertos,
                       ROUND(AVG(ct.total), 2)                                          AS ticket_medio,
                       COALESCE(SUM(ct.itens), 0)                                       AS itens,
                       ROUND(AVG(ct.itens), 1)                                          AS itens_por_conta,
                       COUNT(DISTINCT ct.CPF_Cliente)                                   AS clientes,
                       ROUND(100.0 * COUNT(DISTINCT ct.CPF_Cliente) FILTER (WHERE cl.Fidelidade_Ativa)
                             / NULLIF(COUNT(DISTINCT ct.CPF_Cliente), 0), 1)            AS pct_fieis,
                       COUNT(DISTINCT ct.Data_Atendimento)                              AS noites,
                       ROUND(SUM(ct.total) / NULLIF(COUNT(DISTINCT ct.Data_Atendimento), 0), 2) AS media_noite,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE ct.Status_Conta = 'FECHADA'
                                                       AND fn_situacao_conta(ct.ID_Atendimento) = 'QUITADA')
                             / NULLIF(COUNT(*) FILTER (WHERE ct.Status_Conta = 'FECHADA'), 0), 1) AS pct_quitadas,
                       (SELECT Nome_Produto FROM itens
                         GROUP BY Nome_Produto ORDER BY SUM(Quantidade) DESC, Nome_Produto LIMIT 1) AS produto_top,
                       (SELECT Categoria FROM itens
                         GROUP BY Categoria ORDER BY SUM(Valor_Item) DESC, Categoria LIMIT 1)      AS categoria_top
                  FROM contas ct
                  JOIN CLIENTE cl ON cl.CPF = ct.CPF_Cliente
                """, f);
    }

    /**
     * Tendência do faturamento semanal: reta de mínimos quadrados (REGR_SLOPE/REGR_INTERCEPT)
     * com x = número da semana e a variação da última semana em relação à anterior.
     */
    public Map<String, Object> tendenciaSemanal(Filtro f) {
        return mapa("""
                , semanas AS (
                    SELECT DATE_TRUNC('week', Data_Atendimento) AS semana, SUM(total) AS valor
                      FROM contas
                     GROUP BY 1
                ),
                numeradas AS (
                    SELECT ROW_NUMBER() OVER (ORDER BY semana) AS x, valor,
                           LAG(valor) OVER (ORDER BY semana)   AS anterior,
                           ROW_NUMBER() OVER (ORDER BY semana DESC) AS de_tras
                      FROM semanas
                )
                SELECT COUNT(*)                                                    AS semanas,
                       ROUND(CAST(REGR_SLOPE(valor, x) AS NUMERIC), 2)             AS inclinacao,
                       ROUND(CAST(REGR_INTERCEPT(valor, x) AS NUMERIC), 2)         AS intercepto,
                       ROUND(CAST(REGR_R2(valor, x) AS NUMERIC), 3)                AS r2,
                       MAX(valor) FILTER (WHERE de_tras = 1)                       AS ultima,
                       MAX(anterior) FILTER (WHERE de_tras = 1)                    AS penultima,
                       ROUND(100.0 * (MAX(valor) FILTER (WHERE de_tras = 1)
                                      - MAX(anterior) FILTER (WHERE de_tras = 1))
                             / NULLIF(MAX(anterior) FILTER (WHERE de_tras = 1), 0), 1) AS variacao_pct
                  FROM numeradas
                """, f);
    }

    // ------------------------------------------------------------------ estatística descritiva

    /** Medidas de posição e dispersão do valor consumido por atendimento fechado. */
    public Map<String, Object> estatisticasDescritivas(Filtro f) {
        return mapa("""
                , fechadas AS (SELECT total FROM contas WHERE Status_Conta = 'FECHADA')
                SELECT COUNT(*)                                                               AS n,
                       ROUND(AVG(total), 2)                                                   AS media,
                       ROUND(CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS mediana,
                       MODE() WITHIN GROUP (ORDER BY total)                                   AS moda,
                       (SELECT COUNT(*) FROM fechadas f2
                         WHERE f2.total = (SELECT MODE() WITHIN GROUP (ORDER BY total) FROM fechadas)) AS moda_freq,
                       ROUND(VAR_SAMP(total), 2)                                              AS variancia,
                       ROUND(STDDEV_SAMP(total), 2)                                           AS desvio_padrao,
                       ROUND(100 * STDDEV_SAMP(total) / NULLIF(AVG(total), 0), 1)             AS coef_variacao,
                       MIN(total)                                                             AS minimo,
                       ROUND(CAST(PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS q1,
                       ROUND(CAST(PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS q3,
                       MAX(total)                                                             AS maximo,
                       MAX(total) - MIN(total)                                                AS amplitude,
                       ROUND(3 * (AVG(total) - CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY total) AS NUMERIC))
                             / NULLIF(STDDEV_SAMP(total), 0), 2)                              AS assimetria
                  FROM fechadas
                """, f);
    }

    /** Tabela de frequências (absoluta, relativa e acumulada) em faixas de LARGURA_FAIXA reais. */
    public List<Map<String, Object>> distribuicaoValorAtendimento(Filtro f) {
        return lista("""
                , fechadas AS (SELECT total FROM contas WHERE Status_Conta = 'FECHADA')
                SELECT 'R$ ' || (g.faixa * :largura) || ' a ' || ((g.faixa + 1) * :largura)   AS rotulo,
                       COUNT(t.total)                                                    AS valor,
                       ROUND(100.0 * COUNT(t.total) / NULLIF(SUM(COUNT(t.total)) OVER (), 0), 1) AS relativa,
                       SUM(COUNT(t.total)) OVER (ORDER BY g.faixa)                       AS acumulada
                  FROM GENERATE_SERIES(0, (SELECT CAST(FLOOR(MAX(total) / :largura) AS INTEGER) FROM fechadas)) AS g(faixa)
                  LEFT JOIN fechadas t ON CAST(FLOOR(t.total / :largura) AS INTEGER) = g.faixa
                 GROUP BY g.faixa
                 ORDER BY g.faixa
                """, f);
    }

    // ------------------------------------------------------------------ séries dos gráficos

    /** Faturamento, atendimentos e ticket médio por semana (segunda-feira de cada semana). */
    public List<Map<String, Object>> evolucaoSemanal(Filtro f) {
        return lista("""
                SELECT TO_CHAR(DATE_TRUNC('week', Data_Atendimento), 'DD/MM') AS rotulo,
                       SUM(total)                                           AS faturamento,
                       COUNT(*)                                             AS atendimentos,
                       ROUND(AVG(total), 2)                                 AS ticket
                  FROM contas
                 GROUP BY DATE_TRUNC('week', Data_Atendimento)
                 ORDER BY DATE_TRUNC('week', Data_Atendimento)
                """, f);
    }

    public List<Map<String, Object>> faturamentoPorCategoria(Filtro f) {
        return lista("""
                SELECT Categoria AS rotulo, SUM(Valor_Item) AS valor
                  FROM itens
                 GROUP BY Categoria
                 ORDER BY valor DESC
                """, f);
    }

    public List<Map<String, Object>> produtosMaisVendidos(Filtro f) {
        return lista("""
                SELECT Nome_Produto AS rotulo, SUM(Quantidade) AS valor, SUM(Valor_Item) AS faturamento
                  FROM itens
                 GROUP BY Nome_Produto
                 ORDER BY valor DESC, rotulo
                 LIMIT 10
                """, f);
    }

    /** Pagamentos parciais dos atendimentos que entram no filtro. */
    public List<Map<String, Object>> pagamentosPorMetodo(Filtro f) {
        return lista("""
                SELECT pg.Metodo_Pagamento AS rotulo,
                       SUM(pg.Valor_Pago)  AS valor,
                       COUNT(*)            AS quantidade
                  FROM PAGAMENTO_PARCIAL pg
                 WHERE pg.ID_Atendimento IN (SELECT ID_Atendimento FROM contas)
                 GROUP BY pg.Metodo_Pagamento
                 ORDER BY valor DESC
                """, f);
    }

    /** Itens pedidos por hora do dia (distribuição de frequência do horário do pedido). */
    public List<Map<String, Object>> itensPorHora(Filtro f) {
        return lista("""
                SELECT LPAD(CAST(EXTRACT(HOUR FROM Data_Hora_Pedido) AS INTEGER)::TEXT, 2, '0') || 'h' AS rotulo,
                       SUM(Quantidade)                                                              AS valor
                  FROM itens
                 GROUP BY EXTRACT(HOUR FROM Data_Hora_Pedido)
                 -- a noite passa da meia-noite: ordena começando às 12h (00h vem depois das 23h)
                 ORDER BY MOD(CAST(EXTRACT(HOUR FROM Data_Hora_Pedido) AS INTEGER) + 12, 24)
                """, f);
    }

    /** Média e mediana do valor por conta em cada dia da semana, e atendimentos por noite. */
    public List<Map<String, Object>> porDiaDaSemana(Filtro f) {
        return lista("""
                SELECT (ARRAY['Seg','Ter','Qua','Qui','Sex','Sab','Dom'])[CAST(EXTRACT(ISODOW FROM Data_Atendimento) AS INTEGER)] AS rotulo,
                       ROUND(AVG(total), 2)                                                          AS media,
                       ROUND(CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY total) AS NUMERIC), 2) AS mediana,
                       COUNT(*)                                                                      AS atendimentos,
                       ROUND(1.0 * COUNT(*) / COUNT(DISTINCT Data_Atendimento), 1)                   AS por_noite
                  FROM contas
                 GROUP BY EXTRACT(ISODOW FROM Data_Atendimento)
                 ORDER BY EXTRACT(ISODOW FROM Data_Atendimento)
                """, f);
    }

    /** Participação (%) de cada categoria no faturamento de cada área: base do gráfico radar. */
    public List<Map<String, Object>> categoriaPorArea(Filtro f) {
        return lista("""
                SELECT Localizacao AS area,
                       Categoria   AS categoria,
                       ROUND(100.0 * SUM(Valor_Item) / SUM(SUM(Valor_Item)) OVER (PARTITION BY Localizacao), 1) AS pct
                  FROM itens
                 GROUP BY Localizacao, Categoria
                 ORDER BY Localizacao, Categoria
                """, f);
    }

    // ------------------------------------------------------------------ correlação

    /**
     * Tempo de permanência (abertura da conta até o último pagamento, em minutos) e valor
     * consumido de cada atendimento fechado que entra no filtro.
     */
    private static final String PERMANENCIA = """
            , base AS (
                SELECT ct.ID_Atendimento,
                       ROUND(EXTRACT(EPOCH FROM (MAX(pg.Horario_Pagamento) - a.Data_Hora_Abertura)) / 60) AS minutos,
                       ct.total
                  FROM contas ct
                  JOIN ATENDIMENTO a        ON a.ID_Atendimento = ct.ID_Atendimento
                  JOIN PAGAMENTO_PARCIAL pg ON pg.ID_Atendimento = ct.ID_Atendimento
                 WHERE ct.Status_Conta = 'FECHADA'
                 GROUP BY ct.ID_Atendimento, a.Data_Hora_Abertura, ct.total
            )
            """;

    public List<Map<String, Object>> permanenciaValorPontos(Filtro f) {
        return lista(PERMANENCIA + """
                SELECT ID_Atendimento AS id, minutos AS x, total AS y
                  FROM base
                 ORDER BY minutos
                """, f);
    }

    /**
     * Correlação de Pearson (CORR), r², reta de mínimos quadrados (REGR_SLOPE / REGR_INTERCEPT)
     * e estatística t = r * sqrt(n - 2) / sqrt(1 - r²).
     */
    public Map<String, Object> permanenciaValorCorrelacao(Filtro f) {
        return mapa(PERMANENCIA + """
                SELECT COUNT(*)                                                  AS n,
                       ROUND(CAST(CORR(total, minutos) AS NUMERIC), 3)           AS r,
                       ROUND(CAST(POWER(CORR(total, minutos), 2) AS NUMERIC), 3) AS r2,
                       ROUND(CAST(REGR_SLOPE(total, minutos) AS NUMERIC), 4)     AS inclinacao,
                       ROUND(CAST(REGR_INTERCEPT(total, minutos) AS NUMERIC), 2) AS intercepto,
                       ROUND(CAST(CORR(total, minutos) * SQRT(GREATEST(COUNT(*) - 2, 0))
                                  / NULLIF(SQRT(1 - POWER(CORR(total, minutos), 2)), 0) AS NUMERIC), 3) AS t,
                       COUNT(*) - 2                                              AS gl,
                       MIN(minutos)                                              AS x_min,
                       MAX(minutos)                                              AS x_max,
                       CASE
                           WHEN CORR(total, minutos) IS NULL THEN 'indefinida'
                           WHEN ABS(CORR(total, minutos)) < 0.3 THEN 'fraca'
                           WHEN ABS(CORR(total, minutos)) < 0.7 THEN 'moderada'
                           ELSE 'forte'
                       END || CASE WHEN CORR(total, minutos) < 0 THEN ' negativa'
                                   WHEN CORR(total, minutos) > 0 THEN ' positiva' ELSE '' END AS intensidade
                  FROM base
                """, f);
    }
}
