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
        SELECTS.put("LOG_PRECO_PRODUTO", """
                SELECT ID_Log, Codigo_Prod, Preco_Antigo, Preco_Novo, Data_Alteracao, Usuario
                  FROM LOG_PRECO_PRODUTO ORDER BY ID_Log""");
    }

    /** Visões da Etapa 04 (sql/11_visoes.sql), com a justificativa mostrada na tela. */
    private static final Map<String, String> VISOES = new LinkedHashMap<>();
    private static final Map<String, String> DESCRICOES = Map.of(
            "VW_ITENS_VENDIDOS",
            "Cada item vendido com o contexto do atendimento: produto, valor (Quantidade x Preço), "
                    + "mesa, área, cliente e garçom. Junta 6 tabelas (PEDIDO, PRODUTO, ATENDIMENTO, MESA, "
                    + "PESSOA e FUNCIONARIO) e centraliza a regra do valor do item.",
            "VW_HISTORICO_CLIENTE",
            "Ficha do cliente para o programa de fidelidade: visitas, gasto, ticket médio, categoria "
                    + "favorita, segmento e sugestão de oferecer ou rever a fidelidade. Junta CLIENTE, "
                    + "PESSOA, ATENDIMENTO, PEDIDO e PRODUTO.");

    static {
        VISOES.put("VW_ITENS_VENDIDOS", "SELECT * FROM vw_itens_vendidos ORDER BY ID_Atendimento, ID_Pedido, Codigo_Prod");
        VISOES.put("VW_HISTORICO_CLIENTE", "SELECT * FROM vw_historico_cliente ORDER BY Total_Gasto DESC, Nome");
    }

    private final JdbcTemplate jdbc;

    public TabelaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> nomes() {
        return SELECTS.keySet();
    }

    public Set<String> visoes() {
        return VISOES.keySet();
    }

    public boolean existe(String nome) {
        String chave = nome.toUpperCase();
        return SELECTS.containsKey(chave) || VISOES.containsKey(chave);
    }

    /** Justificativa da visão, ou null para uma tabela. */
    public String descricao(String nome) {
        return DESCRICOES.get(nome.toUpperCase());
    }

    public Optional<ResultadoTabela> conteudo(String nome) {
        String chave = nome.toUpperCase();
        String sql = SELECTS.getOrDefault(chave, VISOES.get(chave));
        return sql == null ? Optional.empty() : Optional.of(jdbc.query(sql, Resultados.EXTRATOR));
    }
}
