package br.cesar.bar.repository;

import br.cesar.bar.model.ResultadoTabela;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Visualização do conteúdo de cada tabela. Os SELECTs são fixos (nada de nome de tabela vindo
 * da URL concatenado no SQL), então só as tabelas listadas aqui podem ser consultadas.
 */
@Repository
public class TabelaRepository {

    private static final Map<String, String> SELECTS = new LinkedHashMap<>();

    static {
        SELECTS.put("PESSOA", "SELECT CPF, Nome, Rua, Numero, Bairro, CEP FROM PESSOA ORDER BY CPF");
        SELECTS.put("TELEFONE", "SELECT CPF, Telefone FROM TELEFONE ORDER BY CPF, Telefone");
        SELECTS.put("CLIENTE", "SELECT CPF, Data_Primeira_Visita, Fidelidade_Ativa FROM CLIENTE ORDER BY CPF");
        SELECTS.put("FUNCIONARIO",
                "SELECT CPF, Matricula, Cargo, Salario, CPF_Supervisor FROM FUNCIONARIO ORDER BY Matricula");
        SELECTS.put("MESA", "SELECT Numero_Mesa, Capacidade, Localizacao FROM MESA ORDER BY Numero_Mesa");
        SELECTS.put("PRODUTO",
                "SELECT Codigo_Prod, Nome_Produto, Preco_Atual, Categoria FROM PRODUTO ORDER BY Codigo_Prod");
        SELECTS.put("ATENDIMENTO", """
                SELECT ID_Atendimento, Data_Hora_Abertura, Status_Conta, CPF_Cliente, CPF_Garcom, Numero_Mesa
                  FROM ATENDIMENTO ORDER BY ID_Atendimento""");
        SELECTS.put("PEDIDO", """
                SELECT ID_Pedido, ID_Atendimento, Codigo_Prod, Quantidade, Data_Hora_Pedido
                  FROM PEDIDO ORDER BY ID_Atendimento, ID_Pedido, Codigo_Prod""");
        SELECTS.put("PAGAMENTO_PARCIAL", """
                SELECT ID_Atendimento, Num_Transacao, Valor_Pago, Metodo_Pagamento, Horario_Pagamento
                  FROM PAGAMENTO_PARCIAL ORDER BY ID_Atendimento, Num_Transacao""");
    }

    private final JdbcTemplate jdbc;

    public TabelaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> nomes() {
        return SELECTS.keySet();
    }

    public Optional<ResultadoTabela> conteudo(String tabela) {
        String sql = SELECTS.get(tabela.toUpperCase());
        return sql == null ? Optional.empty() : Optional.of(jdbc.query(sql, Resultados.EXTRATOR));
    }
}
