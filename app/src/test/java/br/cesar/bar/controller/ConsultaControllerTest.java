package br.cesar.bar.controller;

import br.cesar.bar.TesteDeIntegracao;
import br.cesar.bar.model.Consulta;
import br.cesar.bar.model.ResultadoTabela;
import br.cesar.bar.repository.ConsultaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
class ConsultaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ConsultaRepository consultas;

    @Test
    void listaVaiParaAPrimeiraConsulta() throws Exception {
        mvc.perform(get("/consultas")).andExpect(redirectedUrl("/consultas/faturamento-categoria"));
    }

    /** Todas as consultas e visões rodam com os valores padrão dos filtros e trazem linhas. */
    @ParameterizedTest
    @ValueSource(strings = {"faturamento-categoria", "ranking-atendentes", "conferencia-contas",
            "clientes-acima-media", "hierarquia-supervisao", "mesas-ociosas", "comparativo-dias",
            "todas-categorias", "acima-media-categoria", "vw-itens-vendidos", "vw-historico-cliente"})
    void executaComOsPadroes(String id) throws Exception {
        MvcResult r = mvc.perform(get("/consultas/" + id))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("erro"))
                .andReturn();
        ResultadoTabela res = (ResultadoTabela) r.getModelAndView().getModel().get("resultado");
        assertThat(res.linhas()).isNotEmpty();
        Consulta c = consultas.buscar(id).orElseThrow();
        assertThat(res.colunas()).contains(c.grafico().rotulo());
        c.grafico().valores().forEach(v -> assertThat(res.colunas()).contains(v));
    }

    @Test
    void comparativoComOsDiasDaEtapa04() throws Exception {
        MvcResult r = mvc.perform(get("/consultas/comparativo-dias")).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, String> valores = (Map<String, String>) r.getModelAndView().getModel().get("valores");
        assertThat(valores).containsEntry("diaA", "2026-09-19").containsEntry("diaB", "2026-09-20");
    }

    @Test
    void filtrosRestringemOResultado() throws Exception {
        MvcResult r = mvc.perform(get("/consultas/conferencia-contas")
                        .param("status", "ABERTA").param("situacao", "").param("inicio", "").param("fim", ""))
                .andExpect(status().isOk())
                .andReturn();
        ResultadoTabela res = (ResultadoTabela) r.getModelAndView().getModel().get("resultado");
        int col = res.colunas().indexOf("Status");
        assertThat(res.linhas()).isNotEmpty().allSatisfy(l -> assertThat(l.get(col)).isEqualTo("ABERTA"));
    }

    @Test
    void filtroNumericoEDeLista() throws Exception {
        MvcResult r = mvc.perform(get("/consultas/todas-categorias")
                        .param("fidelidade", "Sim").param("minVisitas", "3"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("erro"))
                .andReturn();
        ResultadoTabela res = (ResultadoTabela) r.getModelAndView().getModel().get("resultado");
        int col = res.colunas().indexOf("Fidelidade");
        assertThat(res.linhas()).allSatisfy(l -> assertThat(l.get(col)).isEqualTo("Sim"));
    }

    @Test
    void valorInvalidoMostraOErroDoBanco() throws Exception {
        mvc.perform(get("/consultas/faturamento-categoria").param("inicio", "data-errada"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("erro"))
                .andExpect(model().attributeDoesNotExist("resultado"));
    }

    @Test
    void consultaInexistente() throws Exception {
        mvc.perform(get("/consultas/nao-existe")).andExpect(status().isNotFound());
    }
}
