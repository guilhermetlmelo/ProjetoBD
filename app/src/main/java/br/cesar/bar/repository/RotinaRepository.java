package br.cesar.bar.repository;

import br.cesar.bar.model.Opcao;
import br.cesar.bar.model.ResultadoTabela;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chamadas às rotinas da Etapa 05 (sql/12_funcoes.sql, 13_procedimentos.sql, 14_triggers.sql)
 * e as consultas que mostram o efeito de cada uma.
 */
@Repository
public class RotinaRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;

    public RotinaRepository(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        // transação própria (REQUIRES_NEW): o ROLLBACK do teste do trigger nunca afeta outra transação
        this.transacao = new TransactionTemplate(tm);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Executa um comando e devolve as mensagens RAISE NOTICE que o PostgreSQL mandou. O driver
     * JDBC entrega cada NOTICE como um SQLWarning encadeado no statement.
     */
    private List<String> executarComAvisos(String sql, Object... args) {
        return jdbc.execute((ConnectionCallback<List<String>>) con -> {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                ps.execute();
                List<String> avisos = new ArrayList<>();
                for (SQLWarning w = ps.getWarnings(); w != null; w = w.getNextWarning()) {
                    avisos.add(w.getMessage());
                }
                return avisos;
            }
        });
    }

    /** Código-fonte de uma função/procedimento como está gravado no banco (pg_get_functiondef). */
    public String codigoFonte(String nome) {
        List<String> def = jdbc.queryForList("""
                SELECT pg_get_functiondef(p.oid)
                  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                 WHERE n.nspname = 'public' AND p.proname = ?
                """, String.class, nome);
        return def.isEmpty() ? "(não encontrado no banco: rode scripts/rodar_tudo.ps1)" : def.get(0).trim();
    }

    // ------------------------------------------------------------------ funções

    /** fn_valor_conta e fn_situacao_conta para um atendimento (a segunda dá erro se ele não existe). */
    public Map<String, Object> funcoesConta(int id) {
        return jdbc.queryForMap("""
                SELECT ? AS id,
                       fn_valor_conta(?)    AS valor,
                       fn_situacao_conta(?) AS situacao,
                       (SELECT COALESCE(SUM(Valor_Pago), 0) FROM PAGAMENTO_PARCIAL WHERE ID_Atendimento = ?) AS pago
                """, id, id, id, id);
    }

    /** Quantidade de contas em cada situação devolvida por fn_situacao_conta. */
    public List<Map<String, Object>> contasPorSituacao() {
        return jdbc.queryForList("""
                SELECT fn_situacao_conta(ID_Atendimento) AS rotulo,
                       COUNT(*)                          AS valor,
                       SUM(fn_valor_conta(ID_Atendimento)) AS consumido
                  FROM ATENDIMENTO
                 GROUP BY 1
                 ORDER BY valor DESC
                """);
    }

    /** Contas que não estão QUITADAS (o que o caixa precisa olhar). */
    public ResultadoTabela contasPendentes() {
        return jdbc.query("""
                SELECT a.ID_Atendimento                                   AS "Atendimento",
                       a.Data_Hora_Abertura                               AS "Abertura",
                       a.Status_Conta                                     AS "Status",
                       p.Nome                                             AS "Cliente",
                       fn_valor_conta(a.ID_Atendimento)                   AS "fn_valor_conta (R$)",
                       fn_situacao_conta(a.ID_Atendimento)                AS "fn_situacao_conta"
                  FROM ATENDIMENTO a
                  JOIN PESSOA p ON p.CPF = a.CPF_Cliente
                 WHERE fn_situacao_conta(a.ID_Atendimento) <> 'QUITADA'
                 ORDER BY a.ID_Atendimento
                """, Resultados.EXTRATOR);
    }

    // ------------------------------------------------------------------ procedimento 1 + trigger 1

    public List<String> reajustarCategoria(String categoria, BigDecimal percentual) {
        return executarComAvisos("CALL sp_reajustar_preco_categoria(CAST(? AS VARCHAR), CAST(? AS NUMERIC))",
                categoria, percentual);
    }

    public List<Map<String, Object>> precoMedioPorCategoria() {
        return jdbc.queryForList("""
                SELECT Categoria AS rotulo, ROUND(AVG(Preco_Atual), 2) AS valor, COUNT(*) AS produtos
                  FROM PRODUTO
                 GROUP BY Categoria
                 ORDER BY Categoria
                """);
    }

    /** O que o trigger trg_log_preco_produto gravou. */
    public ResultadoTabela logPrecos() {
        return jdbc.query("""
                SELECT l.ID_Log                                                    AS "Log",
                       l.Data_Alteracao                                            AS "Quando",
                       pr.Nome_Produto                                             AS "Produto",
                       pr.Categoria                                                AS "Categoria",
                       l.Preco_Antigo                                              AS "Preco antigo (R$)",
                       l.Preco_Novo                                                AS "Preco novo (R$)",
                       ROUND(100 * (l.Preco_Novo - l.Preco_Antigo) / l.Preco_Antigo, 1) AS "Variacao (%)",
                       l.Usuario                                                   AS "Usuario"
                  FROM LOG_PRECO_PRODUTO l
                  JOIN PRODUTO pr ON pr.Codigo_Prod = l.Codigo_Prod
                 ORDER BY l.ID_Log DESC
                 LIMIT 50
                """, Resultados.EXTRATOR);
    }

    public int totalLogs() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM LOG_PRECO_PRODUTO", Integer.class);
    }

    // ------------------------------------------------------------------ procedimento 2 (cursor)

    public List<Map<String, Object>> equipePorGerente() {
        return jdbc.queryForList("""
                SELECT p.Nome AS rotulo, COUNT(s.CPF) AS valor
                  FROM FUNCIONARIO g
                  JOIN PESSOA p ON p.CPF = g.CPF
                  LEFT JOIN FUNCIONARIO s ON s.CPF_Supervisor = g.CPF
                 WHERE g.Cargo = 'Gerente'
                 GROUP BY g.CPF, p.Nome
                 ORDER BY p.Nome
                """);
    }

    public ResultadoTabela semSupervisor() {
        return jdbc.query("""
                SELECT f.Matricula AS "Matricula", p.Nome AS "Funcionario", f.Cargo AS "Cargo"
                  FROM FUNCIONARIO f
                  JOIN PESSOA p ON p.CPF = f.CPF
                 WHERE f.CPF_Supervisor IS NULL AND f.Cargo <> 'Gerente'
                 ORDER BY f.Matricula
                """, Resultados.EXTRATOR);
    }

    public List<Opcao> gerentes() {
        return jdbc.query("""
                SELECT g.CPF, p.Nome || ' (' || COUNT(s.CPF) || ' na equipe)'
                  FROM FUNCIONARIO g
                  JOIN PESSOA p ON p.CPF = g.CPF
                  LEFT JOIN FUNCIONARIO s ON s.CPF_Supervisor = g.CPF
                 WHERE g.Cargo = 'Gerente'
                 GROUP BY g.CPF, p.Nome
                 ORDER BY p.Nome
                """, (rs, i) -> new Opcao(rs.getString(1), rs.getString(2)));
    }

    /**
     * Simula a saída de um gerente: a equipe dele fica sem supervisor, exatamente o que o
     * ON DELETE SET NULL da FK faria se ele fosse excluído.
     */
    public int simularSaidaGerente(String cpfGerente) {
        return jdbc.update("UPDATE FUNCIONARIO SET CPF_Supervisor = NULL WHERE CPF_Supervisor = ?", cpfGerente);
    }

    public List<String> redistribuir() {
        return executarComAvisos("CALL sp_redistribuir_sem_supervisor()");
    }

    // ------------------------------------------------------------------ trigger 2

    /** Contas para o teste do trigger: as abertas e as últimas fechadas. */
    public List<Opcao> contasParaTeste() {
        return jdbc.query("""
                (SELECT ID_Atendimento, 'Nº ' || ID_Atendimento || ' · ' || Status_Conta || ' · ' || p.Nome
                   FROM ATENDIMENTO a JOIN PESSOA p ON p.CPF = a.CPF_Cliente
                  WHERE Status_Conta = 'ABERTA')
                UNION ALL
                (SELECT ID_Atendimento, 'Nº ' || ID_Atendimento || ' · ' || Status_Conta || ' · ' || p.Nome
                   FROM ATENDIMENTO a JOIN PESSOA p ON p.CPF = a.CPF_Cliente
                  WHERE Status_Conta = 'FECHADA'
                  ORDER BY ID_Atendimento
                  LIMIT 15)
                """, (rs, i) -> new Opcao(rs.getString(1), rs.getString(2)));
    }

    public List<Opcao> produtos() {
        return jdbc.query("SELECT Codigo_Prod, Nome_Produto FROM PRODUTO ORDER BY Nome_Produto",
                (rs, i) -> new Opcao(rs.getString(1), rs.getString(2)));
    }

    /**
     * Tenta lançar um item no atendimento dentro de uma transação que sempre é desfeita
     * (ROLLBACK): mostra se o trigger aceita ou recusa sem alterar os dados.
     * Devolve o status da conta; se o trigger recusar, a exceção sobe para o controller.
     */
    public String testarPedido(int idAtendimento, int produto, int quantidade) {
        return transacao.execute(status -> {
            status.setRollbackOnly();
            String situacao = jdbc.queryForObject(
                    "SELECT Status_Conta FROM ATENDIMENTO WHERE ID_Atendimento = ?", String.class, idAtendimento);
            jdbc.update("""
                    INSERT INTO PEDIDO (ID_Pedido, ID_Atendimento, Codigo_Prod, Quantidade, Data_Hora_Pedido)
                    VALUES ((SELECT COALESCE(MAX(ID_Pedido), 0) + 1 FROM PEDIDO WHERE ID_Atendimento = ?),
                            ?, ?, ?, CURRENT_TIMESTAMP)
                    """, idAtendimento, idAtendimento, produto, quantidade);
            return situacao;
        });
    }
}
