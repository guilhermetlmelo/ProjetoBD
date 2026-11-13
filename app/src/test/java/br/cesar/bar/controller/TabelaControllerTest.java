package br.cesar.bar.controller;

import br.cesar.bar.TesteDeIntegracao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
class TabelaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void listaVaiParaAPrimeiraTabela() throws Exception {
        mvc.perform(get("/tabelas")).andExpect(redirectedUrl("/tabelas/PESSOA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PESSOA", "TELEFONE", "CLIENTE", "FUNCIONARIO", "MESA", "PRODUTO", "ATENDIMENTO",
            "PEDIDO", "PAGAMENTO_PARCIAL", "LOG_PRECO_PRODUTO", "VW_ITENS_VENDIDOS", "vw_historico_cliente"})
    void mostraConteudo(String nome) throws Exception {
        mvc.perform(get("/tabelas/" + nome))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("resultado"))
                .andExpect(model().attributeDoesNotExist("erro"));
    }

    @Test
    void tabelaForaDaListaNaoEhConsultada() throws Exception {
        mvc.perform(get("/tabelas/pg_user")).andExpect(status().isNotFound());
    }

    /** Banco criado antes da Etapa 04: a visão não existe e a tela explica o que fazer. */
    @Test
    void visaoAusenteMostraDica() throws Exception {
        jdbc.execute("DROP VIEW vw_historico_cliente");
        mvc.perform(get("/tabelas/VW_HISTORICO_CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("erro", containsString("rodar_tudo.ps1")));
    }

    @Test
    void tabelaAusenteMostraSoOErro() throws Exception {
        jdbc.execute("DROP TABLE LOG_PRECO_PRODUTO");
        mvc.perform(get("/tabelas/LOG_PRECO_PRODUTO"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("erro", containsString("log_preco_produto")));
    }
}
