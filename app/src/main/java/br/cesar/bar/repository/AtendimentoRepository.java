package br.cesar.bar.repository;

import br.cesar.bar.model.Atendimento;
import br.cesar.bar.model.ItemPedido;
import br.cesar.bar.model.Opcao;
import br.cesar.bar.model.Pagamento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * ATENDIMENTO (a conta da mesa), com os itens de PEDIDO e os PAGAMENTO_PARCIAL.
 * O total e a situação da conta vêm das funções fn_valor_conta e fn_situacao_conta
 * (sql/12_funcoes.sql). O trigger trg_pedido_conta_fechada (sql/14_triggers.sql) barra
 * INSERT/UPDATE em PEDIDO quando a conta está FECHADA: o erro dele aparece na tela.
 */
@Repository
public class AtendimentoRepository {

    private static final String SELECT_BASE = """
            SELECT a.ID_Atendimento, a.Data_Hora_Abertura, a.Status_Conta,
                   a.CPF_Cliente, pc.Nome AS Cliente,
                   a.CPF_Garcom, pg.Nome AS Garcom,
                   a.Numero_Mesa, m.Localizacao,
                   fn_valor_conta(a.ID_Atendimento)                          AS Consumido,
                   (SELECT COALESCE(SUM(Valor_Pago), 0) FROM PAGAMENTO_PARCIAL p
                     WHERE p.ID_Atendimento = a.ID_Atendimento)              AS Pago,
                   fn_situacao_conta(a.ID_Atendimento)                       AS Situacao
              FROM ATENDIMENTO a
              JOIN PESSOA pc ON pc.CPF = a.CPF_Cliente
              JOIN PESSOA pg ON pg.CPF = a.CPF_Garcom
              JOIN MESA m    ON m.Numero_Mesa = a.Numero_Mesa
            """;

    private static final RowMapper<Atendimento> MAPPER = (rs, i) -> new Atendimento(
            rs.getInt("ID_Atendimento"),
            rs.getTimestamp("Data_Hora_Abertura").toLocalDateTime(),
            rs.getString("Status_Conta"),
            rs.getString("CPF_Cliente"),
            rs.getString("Cliente"),
            rs.getString("CPF_Garcom"),
            rs.getString("Garcom"),
            rs.getInt("Numero_Mesa"),
            rs.getString("Localizacao"),
            rs.getBigDecimal("Consumido"),
            rs.getBigDecimal("Pago"),
            rs.getString("Situacao"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate nomeado;

    public AtendimentoRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate nomeado) {
        this.jdbc = jdbc;
        this.nomeado = nomeado;
    }

    // ------------------------------------------------------------------ ATENDIMENTO

    /** Lista com filtros (vazio = todos). A situação é filtrada pela própria fn_situacao_conta. */
    public List<Atendimento> listar(String status, String situacao, String inicio, String fim) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("status", vazio(status), Types.VARCHAR)
                .addValue("situacao", vazio(situacao), Types.VARCHAR)
                .addValue("inicio", vazio(inicio), Types.VARCHAR)
                .addValue("fim", vazio(fim), Types.VARCHAR);
        return nomeado.query(SELECT_BASE + """
                 WHERE (CAST(:status AS VARCHAR) IS NULL OR a.Status_Conta = :status)
                   AND (CAST(:situacao AS VARCHAR) IS NULL OR fn_situacao_conta(a.ID_Atendimento) = :situacao)
                   AND (CAST(:inicio AS DATE) IS NULL OR CAST(a.Data_Hora_Abertura AS DATE) >= CAST(:inicio AS DATE))
                   AND (CAST(:fim AS DATE) IS NULL OR CAST(a.Data_Hora_Abertura AS DATE) <= CAST(:fim AS DATE))
                 ORDER BY a.Data_Hora_Abertura DESC, a.ID_Atendimento DESC
                """, p, MAPPER);
    }

    public Optional<Atendimento> buscar(int id) {
        return jdbc.query(SELECT_BASE + " WHERE a.ID_Atendimento = ?", MAPPER, id).stream().findFirst();
    }

    public int proximoId() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(ID_Atendimento), 0) + 1 FROM ATENDIMENTO", Integer.class);
    }

    public void inserir(int id, LocalDateTime abertura, String cliente, String garcom, int mesa) {
        jdbc.update("""
                INSERT INTO ATENDIMENTO (ID_Atendimento, Data_Hora_Abertura, Status_Conta, CPF_Cliente, CPF_Garcom, Numero_Mesa)
                VALUES (?, ?, 'ABERTA', ?, ?, ?)
                """, id, Timestamp.valueOf(abertura), cliente, garcom, mesa);
    }

    public void atualizar(int id, LocalDateTime abertura, String cliente, String garcom, int mesa) {
        jdbc.update("""
                UPDATE ATENDIMENTO
                   SET Data_Hora_Abertura = ?, CPF_Cliente = ?, CPF_Garcom = ?, Numero_Mesa = ?
                 WHERE ID_Atendimento = ?
                """, Timestamp.valueOf(abertura), cliente, garcom, mesa, id);
    }

    /**
     * Fecha a conta só se ela estiver paga: a regra usa fn_situacao_conta.
     * Devolve a situação encontrada (o fechamento acontece em QUITADA, PAGO A MAIS ou SEM CONSUMO).
     */
    public String fechar(int id) {
        String situacao = jdbc.queryForObject("SELECT fn_situacao_conta(?)", String.class, id);
        if ("EM ABERTO".equals(situacao) || "PARCIAL".equals(situacao)) {
            return situacao;
        }
        jdbc.update("UPDATE ATENDIMENTO SET Status_Conta = 'FECHADA' WHERE ID_Atendimento = ?", id);
        return situacao;
    }

    public void reabrir(int id) {
        jdbc.update("UPDATE ATENDIMENTO SET Status_Conta = 'ABERTA' WHERE ID_Atendimento = ?", id);
    }

    /**
     * Exclui a conta inteira. PAGAMENTO_PARCIAL sai por ON DELETE CASCADE; os itens de PEDIDO
     * são apagados antes porque a FK deles não tem cascade.
     */
    @Transactional
    public void excluir(int id) {
        jdbc.update("DELETE FROM PEDIDO WHERE ID_Atendimento = ?", id);
        jdbc.update("DELETE FROM ATENDIMENTO WHERE ID_Atendimento = ?", id);
    }

    // ------------------------------------------------------------------ PEDIDO

    public List<ItemPedido> itens(int id) {
        return jdbc.query("""
                SELECT pe.ID_Pedido, pe.Codigo_Prod, pr.Nome_Produto, pr.Categoria, pe.Quantidade,
                       pr.Preco_Atual, pe.Quantidade * pr.Preco_Atual AS Valor, pe.Data_Hora_Pedido
                  FROM PEDIDO pe
                  JOIN PRODUTO pr ON pr.Codigo_Prod = pe.Codigo_Prod
                 WHERE pe.ID_Atendimento = ?
                 ORDER BY pe.ID_Pedido, pr.Nome_Produto
                """, (rs, i) -> new ItemPedido(
                rs.getInt("ID_Pedido"),
                rs.getInt("Codigo_Prod"),
                rs.getString("Nome_Produto"),
                rs.getString("Categoria"),
                rs.getInt("Quantidade"),
                rs.getBigDecimal("Preco_Atual"),
                rs.getBigDecimal("Valor"),
                rs.getTimestamp("Data_Hora_Pedido").toLocalDateTime()), id);
    }

    public int proximoPedido(int id) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(ID_Pedido), 0) + 1 FROM PEDIDO WHERE ID_Atendimento = ?",
                Integer.class, id);
    }

    public void adicionarItem(int id, int idPedido, int produto, int quantidade) {
        jdbc.update("""
                INSERT INTO PEDIDO (ID_Pedido, ID_Atendimento, Codigo_Prod, Quantidade, Data_Hora_Pedido)
                VALUES (?, ?, ?, ?, ?)
                """, idPedido, id, produto, quantidade, Timestamp.valueOf(agora()));
    }

    public void alterarQuantidade(int id, int idPedido, int produto, int quantidade) {
        jdbc.update("""
                UPDATE PEDIDO SET Quantidade = ?
                 WHERE ID_Atendimento = ? AND ID_Pedido = ? AND Codigo_Prod = ?
                """, quantidade, id, idPedido, produto);
    }

    /**
     * Remove o item só se a conta estiver ABERTA (o trigger cobre INSERT e UPDATE; DELETE em conta
     * fechada também mudaria o total, então é barrado aqui). Devolve quantas linhas saíram.
     */
    public int removerItem(int id, int idPedido, int produto) {
        return jdbc.update("""
                DELETE FROM PEDIDO
                 WHERE ID_Atendimento = ? AND ID_Pedido = ? AND Codigo_Prod = ?
                   AND EXISTS (SELECT 1 FROM ATENDIMENTO a
                                WHERE a.ID_Atendimento = PEDIDO.ID_Atendimento AND a.Status_Conta = 'ABERTA')
                """, id, idPedido, produto);
    }

    // ------------------------------------------------------------------ PAGAMENTO_PARCIAL

    public List<Pagamento> pagamentos(int id) {
        return jdbc.query("""
                SELECT Num_Transacao, Valor_Pago, Metodo_Pagamento, Horario_Pagamento
                  FROM PAGAMENTO_PARCIAL
                 WHERE ID_Atendimento = ?
                 ORDER BY Num_Transacao
                """, (rs, i) -> new Pagamento(
                rs.getInt(1), rs.getBigDecimal(2), rs.getString(3), rs.getTimestamp(4).toLocalDateTime()), id);
    }

    public void adicionarPagamento(int id, BigDecimal valor, String metodo) {
        jdbc.update("""
                INSERT INTO PAGAMENTO_PARCIAL (ID_Atendimento, Num_Transacao, Valor_Pago, Metodo_Pagamento, Horario_Pagamento)
                VALUES (?, (SELECT COALESCE(MAX(Num_Transacao), 0) + 1 FROM PAGAMENTO_PARCIAL WHERE ID_Atendimento = ?),
                        ?, ?, ?)
                """, id, id, valor, metodo, Timestamp.valueOf(agora()));
    }

    /** Remove o pagamento só se a conta estiver ABERTA. Devolve quantas linhas saíram. */
    public int removerPagamento(int id, int numTransacao) {
        return jdbc.update("""
                DELETE FROM PAGAMENTO_PARCIAL
                 WHERE ID_Atendimento = ? AND Num_Transacao = ?
                   AND EXISTS (SELECT 1 FROM ATENDIMENTO a
                                WHERE a.ID_Atendimento = PAGAMENTO_PARCIAL.ID_Atendimento AND a.Status_Conta = 'ABERTA')
                """, id, numTransacao);
    }

    // ------------------------------------------------------------------ opções dos formulários

    public List<Opcao> clientes() {
        return jdbc.query("""
                SELECT c.CPF, p.Nome FROM CLIENTE c JOIN PESSOA p ON p.CPF = c.CPF ORDER BY p.Nome
                """, (rs, i) -> new Opcao(rs.getString(1), rs.getString(2)));
    }

    public List<Opcao> garcons() {
        return jdbc.query("""
                SELECT f.CPF, p.Nome || ' (' || f.Cargo || ')'
                  FROM FUNCIONARIO f JOIN PESSOA p ON p.CPF = f.CPF
                 WHERE f.Cargo IN ('Garcom', 'Garconete', 'Bartender', 'Gerente')
                 ORDER BY p.Nome
                """, (rs, i) -> new Opcao(rs.getString(1), rs.getString(2)));
    }

    public List<Opcao> mesas() {
        return jdbc.query("""
                SELECT Numero_Mesa, 'Mesa ' || Numero_Mesa || ' (' || Localizacao || ', ' || Capacidade || ' lugares)'
                  FROM MESA ORDER BY Numero_Mesa
                """, (rs, i) -> new Opcao(rs.getString(1), rs.getString(2)));
    }

    public List<Opcao> produtos() {
        return jdbc.query("""
                SELECT Codigo_Prod, Nome_Produto || ' (R$ ' || REPLACE(TO_CHAR(Preco_Atual, 'FM9990.00'), '.', ',') || ')'
                  FROM PRODUTO ORDER BY Categoria, Nome_Produto
                """, (rs, i) -> new Opcao(rs.getString(1), rs.getString(2)));
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    private static String vazio(String v) {
        return v.isBlank() ? null : v.trim();
    }
}
