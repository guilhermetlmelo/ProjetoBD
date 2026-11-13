package br.cesar.bar.controller;

import br.cesar.bar.TesteDeIntegracao;
import br.cesar.bar.model.Funcionario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
class FuncionarioControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder salvar(String cpf, String nome, String cargo, String supervisor) {
        return salvar(cpf, nome, cargo, supervisor, "m0099", "2100.00");
    }

    private static MockHttpServletRequestBuilder salvar(String cpf, String nome, String cargo, String supervisor,
                                                        String matricula, String salario) {
        return post("/funcionarios/salvar").param("cpf", cpf).param("nome", nome).param("matricula", matricula)
                .param("cargo", cargo).param("salario", salario).param("cpfSupervisor", supervisor)
                .param("rua", "Rua B").param("numero", "").param("bairro", "").param("cep", "")
                .param("telefones", "81999990000");
    }

    @Test
    void listaFiltroEEdicao() throws Exception {
        mvc.perform(get("/funcionarios"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("edicao", false))
                .andExpect(model().attribute("funcionarios", hasSize(35)));
        mvc.perform(get("/funcionarios").param("cargo", "Gerente"))
                .andExpect(model().attribute("funcionarios", hasSize(3)));
        mvc.perform(get("/funcionarios").param("cargo", " "))
                .andExpect(model().attribute("funcionarios", hasSize(35)));
        mvc.perform(get("/funcionarios").param("editar", "20000000001"))
                .andExpect(model().attribute("edicao", true));
    }

    @Test
    void proximaMatriculaSegueASequencia() throws Exception {
        Funcionario form = (Funcionario) mvc.perform(get("/funcionarios"))
                .andReturn().getModelAndView().getModel().get("form");
        assertThat(form.matricula()).isEqualTo("M0036");
    }

    @Test
    void inserirAlterarExcluir() throws Exception {
        mvc.perform(salvar("200.000.000-99", " Teste Silva ", "Garcom", "20000000001"))
                .andExpect(redirectedUrl("/funcionarios")).andExpect(flash().attributeExists("sucesso"));
        assertThat(jdbc.queryForObject("SELECT Matricula FROM FUNCIONARIO WHERE CPF = '20000000099'", String.class))
                .isEqualTo("M0099");

        mvc.perform(salvar("20000000099", "Teste Souza", "Bartender", "").param("edicao", "true")
                        .param("telefones", "sem numero, (81) 3333-0000"))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(jdbc.queryForObject("SELECT CPF_Supervisor FROM FUNCIONARIO WHERE CPF = '20000000099'", String.class))
                .isNull();
        assertThat(jdbc.queryForList("SELECT Telefone FROM TELEFONE WHERE CPF = '20000000099'", String.class))
                .containsExactlyInAnyOrder("81999990000", "8133330000");

        mvc.perform(post("/funcionarios/20000000099/excluir"))
                .andExpect(flash().attribute("sucesso", "Funcionário 20000000099 excluído."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PESSOA WHERE CPF = '20000000099'", Integer.class)).isZero();
    }

    @Test
    void naoPodeSerSupervisorDeSiMesmo() throws Exception {
        mvc.perform(salvar("20000000002", "Igor", "Bartender", "20000000002").param("edicao", "true"))
                .andExpect(redirectedUrl("/funcionarios?editar=20000000002"))
                .andExpect(flash().attribute("erro", containsString("si mesmo")));
    }

    @Test
    void matriculaRepetidaNaInsercao() throws Exception {
        mvc.perform(salvar("20000000099", "Teste", "Garcom", "", "M0001", "2100.00"))
                .andExpect(redirectedUrl("/funcionarios"))
                .andExpect(flash().attribute("erro", containsString("Já existe")));
    }

    @Test
    void salarioInvalidoNaEdicao() throws Exception {
        mvc.perform(salvar("20000000002", "Igor", "Bartender", "", "M0002", "0").param("edicao", "true"))
                .andExpect(redirectedUrl("/funcionarios?editar=20000000002"))
                .andExpect(flash().attribute("erro", containsString("CHECK")));
    }

    /** ON DELETE SET NULL: a equipe do gerente excluído fica sem supervisor. */
    @Test
    void excluirGerenteDeixaEquipeSemSupervisor() throws Exception {
        mvc.perform(salvar("20000000098", "Gerente Teste", "Gerente", "", "M0098", "6000.00"));
        mvc.perform(salvar("20000000097", "Sub Teste", "Barback", "20000000098", "M0097", "1800.00"));
        mvc.perform(post("/funcionarios/20000000098/excluir"))
                .andExpect(flash().attribute("sucesso", containsString("1 funcionário(s) ficaram sem supervisor")));
        assertThat(jdbc.queryForObject("SELECT CPF_Supervisor FROM FUNCIONARIO WHERE CPF = '20000000097'", String.class))
                .isNull();
    }

    @Test
    void quemJaAtendeuNaoPodeSerExcluido() throws Exception {
        mvc.perform(post("/funcionarios/20000000004/excluir"))
                .andExpect(flash().attribute("erro", containsString("integridade referencial")));
    }
}
