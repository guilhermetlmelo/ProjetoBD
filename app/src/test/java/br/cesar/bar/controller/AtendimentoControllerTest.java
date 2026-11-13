package br.cesar.bar.controller;

import br.cesar.bar.TesteDeIntegracao;
import br.cesar.bar.model.Atendimento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
class AtendimentoControllerTest {

    private static final int NOVO = 5000;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder abrir(int id) {
        return post("/atendimentos/novo").param("id", String.valueOf(id)).param("abertura", "2026-11-12T20:30")
                .param("cliente", "10000000001").param("garcom", "20000000004").param("mesa", "3");
    }

    private String situacao(int id) {
        return jdbc.queryForObject("SELECT fn_situacao_conta(?)", String.class, id);
    }

    @Test
    void listaComFiltros() throws Exception {
        mvc.perform(get("/atendimentos"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("totalConsumido", "totalPago", "proximoId", "clientes"));
        @SuppressWarnings("unchecked")
        List<Atendimento> abertos = (List<Atendimento>) mvc.perform(get("/atendimentos")
                        .param("status", "ABERTA").param("situacao", "").param("inicio", "2026-09-20").param("fim", "2026-09-20"))
                .andExpect(model().attribute("abertos", 5L))
                .andReturn().getModelAndView().getModel().get("atendimentos");
        assertThat(abertos).hasSize(5).allSatisfy(a -> assertThat(a.status()).isEqualTo("ABERTA"));
        mvc.perform(get("/atendimentos").param("situacao", "PAGO A MAIS"))
                .andExpect(model().attribute("atendimentos", hasSize(0)));
    }

    @Test
    void detalheEInexistente() throws Exception {
        mvc.perform(get("/atendimentos/36"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("a", "itens", "pagamentos", "produtos", "metodos"));
        mvc.perform(get("/atendimentos/999999")).andExpect(status().isNotFound());
    }

    /** Ciclo completo de uma conta: abrir, lançar, pagar, fechar, ver o trigger barrar, reabrir, excluir. */
    @Test
    void cicloDaConta() throws Exception {
        mvc.perform(abrir(NOVO)).andExpect(redirectedUrl("/atendimentos/" + NOVO))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(situacao(NOVO)).isEqualTo("SEM CONSUMO");

        mvc.perform(post("/atendimentos/" + NOVO + "/itens").param("idPedido", "1").param("produto", "26")
                .param("quantidade", "1")).andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/atendimentos/" + NOVO + "/itens/1/26/quantidade").param("quantidade", "2"))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(jdbc.queryForObject("SELECT fn_valor_conta(?)", Integer.class, NOVO)).isEqualTo(64);

        mvc.perform(post("/atendimentos/" + NOVO + "/fechar"))
                .andExpect(flash().attribute("erro", containsString("EM ABERTO")));

        mvc.perform(post("/atendimentos/" + NOVO + "/pagamentos").param("valor", "30").param("metodo", "PIX"))
                .andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/atendimentos/" + NOVO + "/fechar"))
                .andExpect(flash().attribute("erro", containsString("PARCIAL")));

        mvc.perform(post("/atendimentos/" + NOVO + "/pagamentos").param("valor", "34").param("metodo", "DEBITO"));
        mvc.perform(post("/atendimentos/" + NOVO + "/fechar"))
                .andExpect(flash().attribute("sucesso", containsString("QUITADA")));
        assertThat(jdbc.queryForObject("SELECT Status_Conta FROM ATENDIMENTO WHERE ID_Atendimento = ?",
                String.class, NOVO)).isEqualTo("FECHADA");

        mvc.perform(post("/atendimentos/" + NOVO + "/reabrir")).andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/atendimentos/" + NOVO + "/salvar").param("abertura", "2026-11-12T21:00")
                        .param("cliente", "10000000002").param("garcom", "20000000003").param("mesa", "4"))
                .andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/atendimentos/" + NOVO + "/pagamentos/2/excluir")).andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/atendimentos/" + NOVO + "/itens/1/26/excluir")).andExpect(flash().attributeExists("sucesso"));

        mvc.perform(post("/atendimentos/" + NOVO + "/excluir"))
                .andExpect(redirectedUrl("/atendimentos")).andExpect(flash().attributeExists("sucesso"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ATENDIMENTO WHERE ID_Atendimento = ?", Integer.class, NOVO))
                .isZero();
    }

    @Test
    void contaSemConsumoPodeSerFechada() throws Exception {
        mvc.perform(abrir(NOVO));
        mvc.perform(post("/atendimentos/" + NOVO + "/fechar"))
                .andExpect(flash().attribute("sucesso", containsString("SEM CONSUMO")));
    }

    @Test
    void triggerBarraPedidoEmContaFechada() throws Exception {
        mvc.perform(post("/atendimentos/1/itens").param("idPedido", "9").param("produto", "1").param("quantidade", "1"))
                .andExpect(redirectedUrl("/atendimentos/1#itens"))
                .andExpect(flash().attribute("erro", containsString("ja esta fechada")));
    }

    @Test
    void triggerBarraAlteracaoEmContaFechada() throws Exception {
        mvc.perform(post("/atendimentos/1/itens/1/6/quantidade").param("quantidade", "5"))
                .andExpect(flash().attribute("erro", containsString("ja esta fechada")));
    }

    @Test
    void numeroDeAtendimentoRepetido() throws Exception {
        mvc.perform(abrir(1)).andExpect(redirectedUrl("/atendimentos"))
                .andExpect(flash().attribute("erro", containsString("Já existe")));
    }

    @Test
    void fecharContaInexistenteMostraOErroDaFuncao() throws Exception {
        mvc.perform(post("/atendimentos/999999/fechar"))
                .andExpect(flash().attribute("erro", containsString("nao existe")));
    }

    @Test
    void formaDePagamentoInvalida() throws Exception {
        mvc.perform(post("/atendimentos/36/pagamentos").param("valor", "10").param("metodo", "CHEQUE"))
                .andExpect(flash().attribute("erro", containsString("CHECK")));
    }

    @Test
    void mesaInexistenteNaEdicao() throws Exception {
        mvc.perform(post("/atendimentos/36/salvar").param("abertura", "2026-09-20T19:40")
                        .param("cliente", "10000000020").param("garcom", "20000000007").param("mesa", "999"))
                .andExpect(flash().attribute("erro", containsString("integridade referencial")));
    }

    /** DELETE não passa pelo trigger, então a aplicação barra a remoção em conta fechada. */
    @Test
    void naoRemoveItemDeContaFechada() throws Exception {
        mvc.perform(post("/atendimentos/1/itens/1/6/excluir"))
                .andExpect(flash().attribute("erro", containsString("conta está fechada")));
        assertThat(jdbc.queryForObject("SELECT fn_valor_conta(1)", Integer.class)).isEqualTo(78);
    }

    @Test
    void naoRemovePagamentoDeContaFechada() throws Exception {
        mvc.perform(post("/atendimentos/1/pagamentos/1/excluir"))
                .andExpect(flash().attribute("erro", containsString("conta está fechada")));
        assertThat(situacao(1)).isEqualTo("QUITADA");
    }
}
