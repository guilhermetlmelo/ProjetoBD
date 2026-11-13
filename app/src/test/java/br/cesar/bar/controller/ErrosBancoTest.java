package br.cesar.bar.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrosBancoTest {

    private static DataIntegrityViolationException erro(String msg, String sqlState) {
        return new DataIntegrityViolationException("falhou", new SQLException(msg, sqlState));
    }

    @ParameterizedTest
    @CsvSource({
            "23505, Já existe um registro",
            "23503, integridade referencial",
            "23514, restrição CHECK",
            "23502, campo obrigatório",
            "22001, tamanho da coluna",
            "P0001, RAISE EXCEPTION",
    })
    void traduzPeloSqlState(String sqlState, String trecho) {
        assertThat(ErrosBanco.mensagem(erro("ERROR: detalhe", sqlState)))
                .contains(trecho)
                .endsWith("detalhe");
    }

    @Test
    void estadoDesconhecidoDevolveSoODetalhe() {
        assertThat(ErrosBanco.mensagem(erro("ERROR: outra coisa", "42601"))).isEqualTo("outra coisa");
    }

    @Test
    void semSqlStateDevolveSoODetalhe() {
        assertThat(ErrosBanco.mensagem(erro("ERRO: sem estado", null))).isEqualTo("sem estado");
    }

    @Test
    void tiraLinhasDeContextoEJuntaODetalhe() {
        String msg = "ERROR: conta fechada\n  Where: PL/pgSQL function x\n\n  Detalhe: chave (1)\n  Onde: linha 3";
        assertThat(ErrosBanco.mensagem(erro(msg, "P0001")))
                .endsWith("conta fechada Detalhe: chave (1)")
                .doesNotContain("Where")
                .doesNotContain("Onde");
    }

    @Test
    void causaQueNaoEhSqlException() {
        DataIntegrityViolationException e =
                new DataIntegrityViolationException("x", new IllegalStateException("ERROR: y"));
        assertThat(ErrosBanco.mensagem(e)).isEqualTo("y");
    }

    @Test
    void mensagemNula() {
        DataIntegrityViolationException e =
                new DataIntegrityViolationException("x", new SQLException((String) null));
        assertThat(ErrosBanco.mensagem(e)).isEmpty();
    }
}
