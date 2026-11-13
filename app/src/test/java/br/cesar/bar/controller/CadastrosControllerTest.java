package br.cesar.bar.controller;

import br.cesar.bar.TesteDeIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD de PRODUTO, MESA e CLIENTE (telas da Etapa 03). */
@TesteDeIntegracao
class CadastrosControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private int contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ------------------------------------------------------------------ PRODUTO

    @Test
    void produtoListaEEdicao() throws Exception {
        mvc.perform(get("/produtos")).andExpect(status().isOk()).andExpect(model().attribute("edicao", false));
        mvc.perform(get("/produtos").param("editar", "1")).andExpect(model().attribute("edicao", true));
        mvc.perform(get("/produtos").param("editar", "999")).andExpect(model().attribute("edicao", false));
    }

    @Test
    void produtoInserirAlterarExcluir() throws Exception {
        mvc.perform(post("/produtos/salvar").param("codigoProd", "31").param("nomeProduto", " Pastel ")
                        .param("precoAtual", "15.00").param("categoria", "Petisco"))
                .andExpect(redirectedUrl("/produtos")).andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/produtos/salvar").param("codigoProd", "31").param("nomeProduto", "Pastel")
                        .param("precoAtual", "16.00").param("categoria", "Petisco").param("edicao", "true"))
                .andExpect(flash().attributeExists("sucesso"));
        // a mudança de preço passou pelo trigger de log
        assertThat(contar("SELECT COUNT(*) FROM LOG_PRECO_PRODUTO WHERE Codigo_Prod = 31")).isEqualTo(1);
        mvc.perform(post("/produtos/31/excluir")).andExpect(flash().attributeExists("sucesso"));
        assertThat(contar("SELECT COUNT(*) FROM PRODUTO WHERE Codigo_Prod = 31")).isZero();
    }

    @Test
    void produtoComNomeRepetido() throws Exception {
        mvc.perform(post("/produtos/salvar").param("codigoProd", "31").param("nomeProduto", "Mojito")
                        .param("precoAtual", "15.00").param("categoria", "Drink"))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("erro", containsString("Já existe")));
    }

    @Test
    void produtoComPrecoInvalidoNaEdicao() throws Exception {
        mvc.perform(post("/produtos/salvar").param("codigoProd", "1").param("nomeProduto", "Heineken Long Neck")
                        .param("precoAtual", "-1").param("categoria", "Cerveja").param("edicao", "true"))
                .andExpect(redirectedUrl("/produtos?editar=1"))
                .andExpect(flash().attribute("erro", containsString("CHECK")));
    }

    @Test
    void produtoJaPedidoNaoPodeSerExcluido() throws Exception {
        mvc.perform(post("/produtos/1/excluir"))
                .andExpect(flash().attribute("erro", containsString("integridade referencial")));
    }

    // ------------------------------------------------------------------ MESA

    @Test
    void mesaListaEEdicao() throws Exception {
        mvc.perform(get("/mesas")).andExpect(status().isOk()).andExpect(model().attribute("edicao", false));
        mvc.perform(get("/mesas").param("editar", "1")).andExpect(model().attribute("edicao", true));
    }

    @Test
    void mesaInserirAlterarExcluir() throws Exception {
        mvc.perform(post("/mesas/salvar").param("numeroMesa", "31").param("capacidade", "4")
                        .param("localizacao", "Varanda"))
                .andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/mesas/salvar").param("numeroMesa", "31").param("capacidade", "8")
                        .param("localizacao", "Varanda").param("edicao", "true"))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(contar("SELECT Capacidade FROM MESA WHERE Numero_Mesa = 31")).isEqualTo(8);
        mvc.perform(post("/mesas/31/excluir")).andExpect(flash().attributeExists("sucesso"));
    }

    @Test
    void mesaDuplicada() throws Exception {
        mvc.perform(post("/mesas/salvar").param("numeroMesa", "1").param("capacidade", "4")
                        .param("localizacao", "Salao interno"))
                .andExpect(redirectedUrl("/mesas")).andExpect(flash().attributeExists("erro"));
    }

    @Test
    void mesaComCapacidadeInvalida() throws Exception {
        mvc.perform(post("/mesas/salvar").param("numeroMesa", "1").param("capacidade", "0")
                        .param("localizacao", "Salao interno").param("edicao", "true"))
                .andExpect(redirectedUrl("/mesas?editar=1")).andExpect(flash().attributeExists("erro"));
    }

    @Test
    void mesaUsadaNaoPodeSerExcluida() throws Exception {
        mvc.perform(post("/mesas/1/excluir")).andExpect(flash().attributeExists("erro"));
    }

    // ------------------------------------------------------------------ CLIENTE

    @Test
    void clienteListaEEdicao() throws Exception {
        mvc.perform(get("/clientes")).andExpect(status().isOk()).andExpect(model().attribute("edicao", false));
        mvc.perform(get("/clientes").param("editar", "10000000001")).andExpect(model().attribute("edicao", true));
    }

    @Test
    void clienteInserirAlterarExcluir() throws Exception {
        mvc.perform(post("/clientes/salvar").param("cpf", "100.000.000-99").param("nome", "Teste Cliente")
                        .param("rua", "Rua A").param("numero", "1").param("bairro", "Centro").param("cep", "")
                        .param("dataPrimeiraVisita", "2026-10-01").param("fidelidadeAtiva", "true")
                        .param("telefones", "(81) 99999-0000, 8133330000"))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(contar("SELECT COUNT(*) FROM TELEFONE WHERE CPF = '10000000099'")).isEqualTo(2);

        mvc.perform(post("/clientes/salvar").param("cpf", "10000000099").param("nome", "Teste Cliente 2")
                        .param("dataPrimeiraVisita", "2026-10-01").param("edicao", "true"))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(contar("SELECT COUNT(*) FROM TELEFONE WHERE CPF = '10000000099'")).isZero();

        mvc.perform(post("/clientes/10000000099/excluir")).andExpect(flash().attributeExists("sucesso"));
        assertThat(contar("SELECT COUNT(*) FROM PESSOA WHERE CPF = '10000000099'")).isZero();
    }

    @Test
    void clienteDuplicado() throws Exception {
        mvc.perform(post("/clientes/salvar").param("cpf", "10000000001").param("nome", "Ana")
                        .param("dataPrimeiraVisita", "2026-10-01"))
                .andExpect(redirectedUrl("/clientes")).andExpect(flash().attributeExists("erro"));
    }

    @Test
    void clienteComNomeGrandeDemaisNaEdicao() throws Exception {
        mvc.perform(post("/clientes/salvar").param("cpf", "10000000001").param("nome", "x".repeat(81))
                        .param("dataPrimeiraVisita", "2026-10-01").param("edicao", "true"))
                .andExpect(redirectedUrl("/clientes?editar=10000000001"))
                .andExpect(flash().attribute("erro", containsString("tamanho")));
    }

    @Test
    void clienteComAtendimentoNaoPodeSerExcluido() throws Exception {
        mvc.perform(post("/clientes/10000000001/excluir")).andExpect(flash().attributeExists("erro"));
    }
}
