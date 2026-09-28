package br.cesar.bar.repository;

import br.cesar.bar.model.Mesa;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MesaRepository {

    private static final RowMapper<Mesa> MAPPER = (rs, i) -> new Mesa(
            rs.getInt("Numero_Mesa"),
            rs.getInt("Capacidade"),
            rs.getString("Localizacao"));

    private final JdbcTemplate jdbc;

    public MesaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Mesa> listar() {
        return jdbc.query("SELECT Numero_Mesa, Capacidade, Localizacao FROM MESA ORDER BY Numero_Mesa", MAPPER);
    }

    public Optional<Mesa> buscar(int numero) {
        return jdbc.query("SELECT Numero_Mesa, Capacidade, Localizacao FROM MESA WHERE Numero_Mesa = ?",
                MAPPER, numero).stream().findFirst();
    }

    public int proximoNumero() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(Numero_Mesa), 0) + 1 FROM MESA", Integer.class);
    }

    public List<String> localizacoes() {
        return jdbc.queryForList("SELECT DISTINCT Localizacao FROM MESA ORDER BY Localizacao", String.class);
    }

    public void inserir(Mesa m) {
        jdbc.update("INSERT INTO MESA (Numero_Mesa, Capacidade, Localizacao) VALUES (?, ?, ?)",
                m.numeroMesa(), m.capacidade(), m.localizacao());
    }

    public void atualizar(Mesa m) {
        jdbc.update("UPDATE MESA SET Capacidade = ?, Localizacao = ? WHERE Numero_Mesa = ?",
                m.capacidade(), m.localizacao(), m.numeroMesa());
    }

    public void excluir(int numero) {
        jdbc.update("DELETE FROM MESA WHERE Numero_Mesa = ?", numero);
    }
}
