package br.cesar.bar.controller;

import br.cesar.bar.TesteDeIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
class RotinaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void telaCarregaTudoECodigoDasRotinas() throws Exception {
        Map<String, Object> m = mvc.perform(get("/rotinas"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("funcao", "erroFuncao"))
                .andReturn().getModelAndView().getModel();
        Map<String, String> fontes = (Map<String, String>) m.get("fontes");
        assertThat(fontes).hasSize(6).allSatisfy((nome, codigo) -> assertThat(codigo).contains(nome));
        assertThat((List<?>) m.get("porSituacao")).isNotEmpty();
        assertThat((List<?>) m.get("equipes")).hasSize(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void funcoesDeUmaConta() throws Exception {
        Map<String, Object> funcao = (Map<String, Object>) mvc.perform(get("/rotinas").param("conta", "36"))
                .andReturn().getModelAndView().getModel().get("funcao");
        assertThat(funcao).containsEntry("situacao", "PARCIAL");
        assertThat(((Number) funcao.get("valor")).doubleValue()).isEqualTo(165.0);
    }

    /**
     * Fora da transação do teste: o erro da função aborta a transação, e a tela ainda faz outras
     * consultas depois (na aplicação cada comando roda em autocommit).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void funcaoComContaInexistente() throws Exception {
        mvc.perform(get("/rotinas").param("conta", "999999"))
                .andExpect(model().attribute("erroFuncao", containsString("nao existe")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reajusteGeraNoticeELogPeloTrigger() throws Exception {
        List<String> avisos = (List<String>) mvc.perform(post("/rotinas/reajuste")
                        .param("categoria", "Cerveja").param("percentual", "10"))
                .andExpect(redirectedUrl("/rotinas#reajuste"))
                .andExpect(flash().attribute("secao", "reajuste"))
                .andExpect(flash().attributeExists("sucesso"))
                .andReturn().getFlashMap().get("avisos");
        assertThat(avisos).containsExactly("5 produto(s) da categoria Cerveja reajustado(s) em 10%");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM LOG_PRECO_PRODUTO", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT Preco_Atual FROM PRODUTO WHERE Codigo_Prod = 1", Double.class))
                .isEqualTo(13.2);
    }

    @Test
    void reajusteInvalidoRecusadoPeloProcedimento() throws Exception {
        mvc.perform(post("/rotinas/reajuste").param("categoria", "Cerveja").param("percentual", "-150"))
                .andExpect(flash().attribute("erro", containsString("Percentual invalido")));
    }

    @Test
    void reajusteDeCategoriaSemProdutos() throws Exception {
        mvc.perform(post("/rotinas/reajuste").param("categoria", "Vinho").param("percentual", "5"))
                .andExpect(flash().attribute("erro", containsString("Nenhum produto")));
    }

    /** Simula a saída de um gerente e redistribui a equipe com o procedimento de cursor. */
    @Test
    @SuppressWarnings("unchecked")
    void simularSaidaERedistribuir() throws Exception {
        mvc.perform(post("/rotinas/simular-saida").param("gerente", "20000000005"))
                .andExpect(flash().attribute("sucesso", containsString("11 funcionário(s)")));
        List<String> avisos = (List<String>) mvc.perform(post("/rotinas/redistribuir"))
                .andExpect(flash().attributeExists("sucesso"))
                .andReturn().getFlashMap().get("avisos");
        assertThat(avisos).hasSize(12).last().isEqualTo("11 funcionario(s) redistribuido(s)");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM FUNCIONARIO WHERE CPF_Supervisor IS NULL AND Cargo <> 'Gerente'", Integer.class))
                .isZero();
    }

    @Test
    void redistribuirSemGerenteCadastrado() throws Exception {
        jdbc.update("UPDATE FUNCIONARIO SET Cargo = 'Supervisor' WHERE Cargo = 'Gerente'");
        mvc.perform(post("/rotinas/redistribuir"))
                .andExpect(flash().attribute("erro", containsString("Nao ha gerente")));
    }

    @Test
    void triggerAceitaContaAbertaSemGravar() throws Exception {
        int antes = jdbc.queryForObject("SELECT COUNT(*) FROM PEDIDO", Integer.class);
        mvc.perform(post("/rotinas/testar-pedido").param("atendimento", "36").param("produto", "1"))
                .andExpect(redirectedUrl("/rotinas#trigger"))
                .andExpect(flash().attribute("sucesso", containsString("ROLLBACK")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PEDIDO", Integer.class)).isEqualTo(antes);
    }

    @Test
    void triggerRecusaContaFechada() throws Exception {
        mvc.perform(post("/rotinas/testar-pedido").param("atendimento", "1").param("produto", "1")
                        .param("quantidade", "2"))
                .andExpect(flash().attribute("erro", containsString("ja esta fechada")));
    }

    @Test
    void contasParaTesteTemAbertasEFechadas() throws Exception {
        mvc.perform(get("/rotinas"))
                .andExpect(model().attribute("contasTeste",
                        hasItem(org.hamcrest.Matchers.hasToString(containsString("FECHADA")))))
                .andExpect(model().attribute("contasTeste",
                        hasItem(org.hamcrest.Matchers.hasToString(containsString("ABERTA")))));
    }
}
