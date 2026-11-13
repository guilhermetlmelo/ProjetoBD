package br.cesar.bar.repository;

import br.cesar.bar.model.Funcionario;
import br.cesar.bar.model.Opcao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Funcionário também é especialização de PESSOA: cada operação mexe em PESSOA, FUNCIONARIO e
 * TELEFONE numa única transação. CPF_Supervisor é o autorrelacionamento de FUNCIONARIO.
 */
@Repository
public class FuncionarioRepository {

    private static final String SELECT_BASE = """
            SELECT p.CPF, p.Nome, p.Rua, p.Numero, p.Bairro, p.CEP,
                   f.Matricula, f.Cargo, f.Salario, f.CPF_Supervisor,
                   ps.Nome AS Nome_Supervisor,
                   (SELECT COALESCE(STRING_AGG(t.Telefone, ', ' ORDER BY t.Telefone), '')
                      FROM TELEFONE t WHERE t.CPF = p.CPF)                      AS Telefones,
                   (SELECT COUNT(*) FROM ATENDIMENTO a WHERE a.CPF_Garcom = f.CPF) AS Atendimentos
              FROM FUNCIONARIO f
              JOIN PESSOA p       ON p.CPF = f.CPF
              LEFT JOIN PESSOA ps ON ps.CPF = f.CPF_Supervisor
            """;

    private static final RowMapper<Funcionario> MAPPER = (rs, i) -> new Funcionario(
            rs.getString("CPF"),
            rs.getString("Nome"),
            rs.getString("Rua"),
            rs.getString("Numero"),
            rs.getString("Bairro"),
            rs.getString("CEP"),
            rs.getString("Matricula"),
            rs.getString("Cargo"),
            rs.getBigDecimal("Salario"),
            rs.getString("CPF_Supervisor"),
            rs.getString("Nome_Supervisor"),
            rs.getString("Telefones"),
            rs.getInt("Atendimentos"));

    private final JdbcTemplate jdbc;

    public FuncionarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Funcionario> listar(String cargo) {
        if (cargo == null || cargo.isBlank()) {
            return jdbc.query(SELECT_BASE + " ORDER BY f.Matricula", MAPPER);
        }
        return jdbc.query(SELECT_BASE + " WHERE f.Cargo = ? ORDER BY f.Matricula", MAPPER, cargo);
    }

    public Optional<Funcionario> buscar(String cpf) {
        return jdbc.query(SELECT_BASE + " WHERE f.CPF = ?", MAPPER, cpf).stream().findFirst();
    }

    public List<String> cargos() {
        return jdbc.queryForList("SELECT DISTINCT Cargo FROM FUNCIONARIO ORDER BY 1", String.class);
    }

    /** Possíveis supervisores: qualquer funcionário (o select mostra nome e cargo). */
    public List<Opcao> supervisores() {
        return jdbc.query("""
                SELECT f.CPF, p.Nome || ' (' || f.Cargo || ')' AS texto
                  FROM FUNCIONARIO f JOIN PESSOA p ON p.CPF = f.CPF
                 ORDER BY (f.Cargo <> 'Gerente'), p.Nome
                """, (rs, i) -> new Opcao(rs.getString(1), rs.getString(2)));
    }

    public String proximaMatricula() {
        return jdbc.queryForObject("""
                SELECT 'M' || LPAD(CAST(COALESCE(MAX(CAST(SUBSTRING(Matricula FROM 2) AS INTEGER)), 0) + 1 AS TEXT), 4, '0')
                  FROM FUNCIONARIO
                 WHERE Matricula ~ '^M[0-9]+$'
                """, String.class);
    }

    @Transactional
    public void inserir(Funcionario f) {
        jdbc.update("INSERT INTO PESSOA (CPF, Nome, Rua, Numero, Bairro, CEP) VALUES (?, ?, ?, ?, ?, ?)",
                f.cpf(), f.nome(), f.rua(), f.numero(), f.bairro(), f.cep());
        jdbc.update("""
                INSERT INTO FUNCIONARIO (CPF, Matricula, Cargo, Salario, CPF_Supervisor)
                VALUES (?, ?, ?, ?, ?)
                """, f.cpf(), f.matricula(), f.cargo(), f.salario(), f.cpfSupervisor());
        inserirTelefones(f.cpf(), f.telefones());
    }

    @Transactional
    public void atualizar(Funcionario f) {
        if (f.cpf().equals(f.cpfSupervisor())) {
            throw new IllegalArgumentException("Um funcionário não pode ser supervisor de si mesmo.");
        }
        jdbc.update("UPDATE PESSOA SET Nome = ?, Rua = ?, Numero = ?, Bairro = ?, CEP = ? WHERE CPF = ?",
                f.nome(), f.rua(), f.numero(), f.bairro(), f.cep(), f.cpf());
        jdbc.update("""
                UPDATE FUNCIONARIO
                   SET Matricula = ?, Cargo = ?, Salario = ?, CPF_Supervisor = ?
                 WHERE CPF = ?
                """, f.matricula(), f.cargo(), f.salario(), f.cpfSupervisor(), f.cpf());
        jdbc.update("DELETE FROM TELEFONE WHERE CPF = ?", f.cpf());
        inserirTelefones(f.cpf(), f.telefones());
    }

    /**
     * Remove o funcionário e a pessoa. Os supervisionados dele ficam sem supervisor
     * (FK ON DELETE SET NULL) e podem ser redistribuídos pelo procedimento
     * sp_redistribuir_sem_supervisor. Quem já atendeu mesa é barrado pela FK de ATENDIMENTO.
     */
    @Transactional
    public int excluir(String cpf) {
        int subordinados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM FUNCIONARIO WHERE CPF_Supervisor = ?", Integer.class, cpf);
        jdbc.update("DELETE FROM FUNCIONARIO WHERE CPF = ?", cpf);
        jdbc.update("DELETE FROM TELEFONE WHERE CPF = ?", cpf);
        jdbc.update("DELETE FROM PESSOA WHERE CPF = ?", cpf);
        return subordinados;
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
