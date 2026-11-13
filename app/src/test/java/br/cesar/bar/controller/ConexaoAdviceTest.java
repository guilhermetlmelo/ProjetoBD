package br.cesar.bar.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.ui.ExtendedModelMap;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConexaoAdviceTest {

    @Test
    void mostraPaginaDeErroComODetalhe() {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = new ConexaoAdvice().semConexao(
                new CannotGetJdbcConnectionException("sem banco", new SQLException("Connection refused")), model);
        assertThat(view).isEqualTo("erro-conexao");
        assertThat(model.get("detalhe")).isEqualTo("Connection refused");
    }
}
