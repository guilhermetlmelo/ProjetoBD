package br.cesar.bar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class BarApplicationTest {

    /** Sobe a aplicação pelo main, sem servidor web, só para garantir que o contexto inicia. */
    @Test
    void mainSobeOContexto() {
        assertThatCode(() -> BarApplication.main(new String[]{"--spring.main.web-application-type=none"}))
                .doesNotThrowAnyException();
    }
}
