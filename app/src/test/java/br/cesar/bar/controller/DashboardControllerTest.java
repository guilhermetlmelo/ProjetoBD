package br.cesar.bar.controller;

import br.cesar.bar.TesteDeIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@TesteDeIntegracao
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @SuppressWarnings("unchecked")
    void semFiltroUsaOPeriodoInteiroDoBanco() throws Exception {
        MvcResult r = mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("pearsonOk", true))
                .andExpect(model().attribute("significativo", true))
                .andReturn();

        Map<String, Object> m = r.getModelAndView().getModel();
        Map<String, Object> ind = (Map<String, Object>) m.get("ind");
        assertThat(((Number) ind.get("atendimentos")).intValue()).isGreaterThan(40);
        assertThat(((Number) ind.get("pct_quitadas")).doubleValue()).isEqualTo(100.0);
        assertThat((List<?>) m.get("semanal")).hasSizeGreaterThan(5);
        assertThat((List<?>) m.get("porCategoria")).hasSize(6);
        assertThat((List<?>) m.get("categoriaArea")).isNotEmpty();
        assertThat((List<?>) m.get("porDiaSemana")).isNotEmpty();
        assertThat(m.get("classeModal")).isNotEqualTo("-");

        Map<String, Object> estat = (Map<String, Object>) m.get("estat");
        assertThat(estat).containsKeys("media", "mediana", "moda", "variancia", "desvio_padrao", "assimetria");
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtrosDeCategoriaEArea() throws Exception {
        MvcResult r = mvc.perform(get("/")
                        .param("inicio", "2026-09-01").param("fim", "2026-09-20")
                        .param("categoria", "Chopp").param("area", "Salao interno"))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> categorias = (List<Map<String, Object>>) r.getModelAndView().getModel().get("porCategoria");
        assertThat(categorias).extracting(c -> c.get("rotulo")).containsOnly("Chopp");
    }

    @Test
    @SuppressWarnings("unchecked")
    void periodoSemMovimento() throws Exception {
        MvcResult r = mvc.perform(get("/").param("inicio", "2030-01-01").param("fim", "2030-01-02"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pearsonOk", false))
                .andExpect(model().attribute("classeModal", "-"))
                .andReturn();
        Map<String, Object> ind = (Map<String, Object>) r.getModelAndView().getModel().get("ind");
        assertThat(((Number) ind.get("atendimentos")).intValue()).isZero();
    }

    @Test
    void soDataInicialSemDataFinal() throws Exception {
        mvc.perform(get("/").param("inicio", "2026-09-19"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pearsonOk", true));
    }

    /** Com 1 conta não existe correlação: r e t vêm NULL do banco e a tela avisa. */
    @Test
    void filtroComUmaContaSoNaoCalculaCorrelacao() throws Exception {
        mvc.perform(get("/").param("inicio", "2026-07-02").param("fim", "2026-07-02")
                        .param("categoria", "").param("area", "Balcao"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pearsonOk", false));
    }

    @Test
    void soDataFinalSemDataInicial() throws Exception {
        mvc.perform(get("/").param("fim", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pearsonOk", true));
    }

    /** Com 2 contas r = 1 ou -1, mas t = r * raiz(0) / 0 fica indefinido: também não há teste. */
    @Test
    void filtroComDuasContasNaoTemTesteT() throws Exception {
        mvc.perform(get("/").param("inicio", "2026-07-02").param("fim", "2026-07-02").param("area", "Salao interno"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pearsonOk", false));
    }
}
