package br.cesar.bar.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelosTest {

    @Test
    void saldoDoAtendimento() {
        Atendimento a = new Atendimento(1, LocalDateTime.now(), "ABERTA", "1", "Ana", "2", "Igor", 3, "Balcao",
                new BigDecimal("165.00"), new BigDecimal("82.00"), "PARCIAL");
        assertThat(a.saldo()).isEqualByComparingTo("83.00");
    }

    @Test
    void fabricasDeParametro() {
        assertThat(Parametro.data("inicio", "De", "2026-09-19"))
                .extracting(Parametro::tipo, Parametro::padrao).containsExactly("data", "2026-09-19");
        assertThat(Parametro.lista("s", "S", List.of("A")).opcoes()).containsExactly("A");
        assertThat(Parametro.listaDoBanco("a", "A", "SELECT 1").sqlOpcoes()).isEqualTo("SELECT 1");
        assertThat(Parametro.numero("n", "N", "").tipo()).isEqualTo("numero");
    }

    @Test
    void fabricasDeGrafico() {
        assertThat(Grafico.linhas("bar", "X", "T", "A", "B").valores()).containsExactly("A", "B");
        assertThat(Grafico.soma("barh", "X", "T", "V").modo()).isEqualTo("soma");
        assertThat(Grafico.contagem("pie", "X", "T").valores()).isEmpty();
    }
}
