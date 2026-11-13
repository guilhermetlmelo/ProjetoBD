package br.cesar.bar.controller;

import br.cesar.bar.repository.AtendimentoRepository;
import br.cesar.bar.repository.ConsultaRepository;
import br.cesar.bar.repository.RotinaRepository;
import br.cesar.bar.repository.TabelaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Caminhos de falha que o banco real não provoca sob demanda (queda de conexão no meio da
 * página, falha ao excluir). Os repositórios são simulados, então este teste não usa o banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FalhasDeBancoTest {

    private static final CannotGetJdbcConnectionException SEM_CONEXAO =
            new CannotGetJdbcConnectionException("sem banco", new SQLException("Connection refused"));
    private static final DataIntegrityViolationException FALHA =
            new DataIntegrityViolationException("x", new SQLException("ERROR: falhou", "XX000"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultaRepository consultas;

    @MockitoBean
    private TabelaRepository tabelas;

    @MockitoBean
    private RotinaRepository rotinas;

    @MockitoBean
    private AtendimentoRepository atendimentos;

    @Test
    void consultaSemConexaoMostraPaginaDeErro() throws Exception {
        when(consultas.buscar("x")).thenReturn(Optional.of(new br.cesar.bar.model.Consulta(
                "x", "03", "X", "X", java.util.List.of(), java.util.List.of(), null, "SELECT 1")));
        when(consultas.opcoes(any())).thenThrow(SEM_CONEXAO);
        mvc.perform(get("/consultas/x")).andExpect(view().name("erro-conexao"));
    }

    @Test
    void tabelaSemConexaoMostraPaginaDeErro() throws Exception {
        when(tabelas.existe("PESSOA")).thenReturn(true);
        when(tabelas.conteudo("PESSOA")).thenThrow(SEM_CONEXAO);
        mvc.perform(get("/tabelas/PESSOA")).andExpect(view().name("erro-conexao"));
    }

    @Test
    void rotinaSemConexaoMostraPaginaDeErro() throws Exception {
        when(rotinas.funcoesConta(anyInt())).thenThrow(SEM_CONEXAO);
        mvc.perform(get("/rotinas").param("conta", "1")).andExpect(view().name("erro-conexao"));
    }

    @Test
    void falhaAoSimularSaidaDeGerente() throws Exception {
        when(rotinas.simularSaidaGerente(anyString())).thenThrow(FALHA);
        mvc.perform(post("/rotinas/simular-saida").param("gerente", "20000000001"))
                .andExpect(flash().attribute("erro", "falhou"));
    }

    @Test
    void falhaAoExcluirAtendimento() throws Exception {
        doThrow(FALHA).when(atendimentos).excluir(7);
        mvc.perform(post("/atendimentos/7/excluir"))
                .andExpect(redirectedUrl("/atendimentos/7"))
                .andExpect(flash().attribute("erro", "falhou"));
    }

    @Test
    void falhaAoReabrirConta() throws Exception {
        doThrow(FALHA).when(atendimentos).reabrir(7);
        mvc.perform(post("/atendimentos/7/reabrir")).andExpect(flash().attribute("erro", "falhou"));
    }
}
