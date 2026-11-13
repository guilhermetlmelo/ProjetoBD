package br.cesar.bar.estatistica;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TesteTTest {

    @Test
    void valoresConhecidosDaTabelaT() {
        // t crítico bilateral de 5% com 10 graus de liberdade = 2,228
        assertThat(TesteT.pValorBilateral(2.228, 10)).isCloseTo(0.05, within(0.001));
        // t crítico bilateral de 1% com 30 graus de liberdade = 2,750
        assertThat(TesteT.pValorBilateral(2.750, 30)).isCloseTo(0.01, within(0.001));
        // simétrico no sinal de t
        assertThat(TesteT.pValorBilateral(-2.228, 10)).isCloseTo(0.05, within(0.001));
    }

    @Test
    void ramosDaFracaoContinua() {
        // t pequeno: x perto de 1, usa o ramo 1 - I(1-x)
        assertThat(TesteT.pValorBilateral(0.1, 5)).isCloseTo(0.9242, within(0.001));
        // t grande: x perto de 0, usa o ramo direto
        assertThat(TesteT.pValorBilateral(11.08, 242)).isLessThan(1e-20);
    }

    @Test
    void casosLimite() {
        assertThat(TesteT.pValorBilateral(0, 10)).isEqualTo(1.0);
        assertThat(TesteT.pValorBilateral(Double.POSITIVE_INFINITY, 10)).isEqualTo(0.0);
        assertThat(TesteT.pValorBilateral(Double.NaN, 10)).isNaN();
        assertThat(TesteT.pValorBilateral(2.0, 0)).isNaN();
        assertThat(TesteT.pValorBilateral(1e200, 10)).isEqualTo(0.0);
    }

    @Test
    void protecaoContraDivisaoPorZero() {
        assertThat(TesteT.naoZero(0.0)).isEqualTo(1e-300);
        assertThat(TesteT.naoZero(-0.5)).isEqualTo(-0.5);
    }

    @Test
    void fracaoContinuaParaNoLimiteDeIteracoes() {
        double comUma = TesteT.fracaoContinua(5, 0.5, 0.5, 1);
        double completa = TesteT.fracaoContinua(5, 0.5, 0.5, 300);
        assertThat(comUma).isNotEqualTo(completa);
    }
}
