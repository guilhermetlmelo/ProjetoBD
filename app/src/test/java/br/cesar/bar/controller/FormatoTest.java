package br.cesar.bar.controller;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FormatoTest {

    private final Formato fmt = new Formato();

    @Test
    void reais() {
        assertThat(fmt.reais(new BigDecimal("15658"))).isEqualTo("R$ 15.658,00");
        assertThat(fmt.reais(null)).isEqualTo("-");
    }

    @Test
    void numeroComCasas() {
        assertThat(fmt.num(1234.5, 1)).isEqualTo("1.234,5");
        assertThat(fmt.num(7, 0)).isEqualTo("7");
        assertThat(fmt.num(null, 2)).isEqualTo("-");
    }

    @Test
    void percentual() {
        assertThat(fmt.pct(new BigDecimal("46.7"))).isEqualTo("46,7%");
        assertThat(fmt.pct(null)).isEqualTo("-");
    }

    @Test
    void variacaoComSinal() {
        assertThat(fmt.variacao(new BigDecimal("42.7"))).isEqualTo("+42,7%");
        assertThat(fmt.variacao(new BigDecimal("-3"))).isEqualTo("-3,0%");
        assertThat(fmt.variacao(BigDecimal.ZERO)).isEqualTo("0,0%");
        assertThat(fmt.variacao(null)).isEqualTo("-");
    }

    @Test
    void celula() {
        assertThat(fmt.celula(new BigDecimal("15658.00"))).isEqualTo("15.658,00");
        assertThat(fmt.celula(new BigDecimal("1001"))).isEqualTo("1001");
        assertThat(fmt.celula(1001)).isEqualTo("1001");
        assertThat(fmt.celula(2.5d)).isEqualTo("2,50");
        assertThat(fmt.celula(1.25f)).isEqualTo("1,25");
        assertThat(fmt.celula("Petisco")).isEqualTo("Petisco");
        assertThat(fmt.celula(null)).isEqualTo("-");
    }

    @Test
    void data() {
        assertThat(fmt.data("2026-09-19")).isEqualTo("19/09/2026");
        assertThat(fmt.data(LocalDateTime.of(2026, 9, 19, 21, 5))).isEqualTo("19/09/2026 21:05");
        assertThat(fmt.data(null)).isEqualTo("-");
        assertThat(fmt.data(" ")).isEqualTo("-");
    }
}
