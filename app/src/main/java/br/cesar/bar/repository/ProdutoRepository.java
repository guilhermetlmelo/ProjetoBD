package br.cesar.bar.repository;

import br.cesar.bar.model.Produto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProdutoRepository {

    private static final RowMapper<Produto> MAPPER = (rs, i) -> new Produto(
            rs.getInt("Codigo_Prod"),
            rs.getString("Nome_Produto"),
            rs.getBigDecimal("Preco_Atual"),
            rs.getString("Categoria"));

    private final JdbcTemplate jdbc;

    public ProdutoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Produto> listar() {
        return jdbc.query(
                "SELECT Codigo_Prod, Nome_Produto, Preco_Atual, Categoria FROM PRODUTO ORDER BY Codigo_Prod",
                MAPPER);
    }

    public Optional<Produto> buscar(int codigo) {
        return jdbc.query(
                "SELECT Codigo_Prod, Nome_Produto, Preco_Atual, Categoria FROM PRODUTO WHERE Codigo_Prod = ?",
                MAPPER, codigo).stream().findFirst();
    }

    public int proximoCodigo() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(Codigo_Prod), 0) + 1 FROM PRODUTO", Integer.class);
    }

    public List<String> categorias() {
        return jdbc.queryForList("SELECT DISTINCT Categoria FROM PRODUTO ORDER BY Categoria", String.class);
    }

    public void inserir(Produto p) {
        jdbc.update(
                "INSERT INTO PRODUTO (Codigo_Prod, Nome_Produto, Preco_Atual, Categoria) VALUES (?, ?, ?, ?)",
                p.codigoProd(), p.nomeProduto(), p.precoAtual(), p.categoria());
    }

    public void atualizar(Produto p) {
        jdbc.update(
                "UPDATE PRODUTO SET Nome_Produto = ?, Preco_Atual = ?, Categoria = ? WHERE Codigo_Prod = ?",
                p.nomeProduto(), p.precoAtual(), p.categoria(), p.codigoProd());
    }

    public void excluir(int codigo) {
        jdbc.update("DELETE FROM PRODUTO WHERE Codigo_Prod = ?", codigo);
    }
}
