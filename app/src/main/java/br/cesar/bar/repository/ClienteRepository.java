package br.cesar.bar.repository;

import br.cesar.bar.model.Cliente;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Cliente é uma especialização de PESSOA: cada operação mexe em PESSOA, CLIENTE e TELEFONE
 * dentro de uma única transação (se um comando falhar, nenhum fica gravado).
 */
@Repository
public class ClienteRepository {

    private static final String SELECT_BASE = """
            SELECT p.CPF, p.Nome, p.Rua, p.Numero, p.Bairro, p.CEP,
                   c.Data_Primeira_Visita, c.Fidelidade_Ativa,
                   COALESCE(STRING_AGG(t.Telefone, ', ' ORDER BY t.Telefone), '') AS Telefones
              FROM CLIENTE c
              JOIN PESSOA p ON p.CPF = c.CPF
              LEFT JOIN TELEFONE t ON t.CPF = p.CPF
            """;

    private static final String GROUP_BY = """
             GROUP BY p.CPF, p.Nome, p.Rua, p.Numero, p.Bairro, p.CEP,
                      c.Data_Primeira_Visita, c.Fidelidade_Ativa
            """;

    private static final RowMapper<Cliente> MAPPER = (rs, i) -> new Cliente(
            rs.getString("CPF"),
            rs.getString("Nome"),
            rs.getString("Rua"),
            rs.getString("Numero"),
            rs.getString("Bairro"),
            rs.getString("CEP"),
            rs.getObject("Data_Primeira_Visita", LocalDate.class),
            rs.getBoolean("Fidelidade_Ativa"),
            rs.getString("Telefones"));

    private final JdbcTemplate jdbc;

    public ClienteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Cliente> listar() {
        return jdbc.query(SELECT_BASE + GROUP_BY + " ORDER BY p.Nome", MAPPER);
    }

    public Optional<Cliente> buscar(String cpf) {
        return jdbc.query(SELECT_BASE + " WHERE c.CPF = ?" + GROUP_BY, MAPPER, cpf).stream().findFirst();
    }

    @Transactional
    public void inserir(Cliente c) {
        jdbc.update("INSERT INTO PESSOA (CPF, Nome, Rua, Numero, Bairro, CEP) VALUES (?, ?, ?, ?, ?, ?)",
                c.cpf(), c.nome(), c.rua(), c.numero(), c.bairro(), c.cep());
        jdbc.update("INSERT INTO CLIENTE (CPF, Data_Primeira_Visita, Fidelidade_Ativa) VALUES (?, ?, ?)",
                c.cpf(), c.dataPrimeiraVisita(), c.fidelidadeAtiva());
        inserirTelefones(c.cpf(), c.telefones());
    }

    @Transactional
    public void atualizar(Cliente c) {
        jdbc.update("UPDATE PESSOA SET Nome = ?, Rua = ?, Numero = ?, Bairro = ?, CEP = ? WHERE CPF = ?",
                c.nome(), c.rua(), c.numero(), c.bairro(), c.cep(), c.cpf());
        jdbc.update("UPDATE CLIENTE SET Data_Primeira_Visita = ?, Fidelidade_Ativa = ? WHERE CPF = ?",
                c.dataPrimeiraVisita(), c.fidelidadeAtiva(), c.cpf());
        // Telefone é atributo multivalorado: a lista inteira é substituída.
        jdbc.update("DELETE FROM TELEFONE WHERE CPF = ?", c.cpf());
        inserirTelefones(c.cpf(), c.telefones());
    }

    /**
     * Remove o cliente e a pessoa. TELEFONE sai junto por ON DELETE CASCADE.
     * Se o cliente já tiver atendimentos, a FK de ATENDIMENTO barra a exclusão e nada é apagado.
     */
    @Transactional
    public void excluir(String cpf) {
        jdbc.update("DELETE FROM CLIENTE WHERE CPF = ?", cpf);
        jdbc.update("DELETE FROM PESSOA WHERE CPF = ?", cpf);
    }

    private void inserirTelefones(String cpf, String telefones) {
        List<String> lista = Arrays.stream(telefones.split("[,;]+"))
                .map(t -> t.replaceAll("\\D", ""))
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
        for (String telefone : lista) {
            jdbc.update("INSERT INTO TELEFONE (CPF, Telefone) VALUES (?, ?)", cpf, telefone);
        }
    }
}
