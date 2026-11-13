package br.cesar.bar.controller;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatação no padrão brasileiro para os templates: ${@fmt.reais(valor)}.
 * Valor nulo (filtro sem dados) aparece como "-".
 */
@Component("fmt")
public class Formato {

    private static final DecimalFormatSymbols BR = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public String reais(Object valor) {
        return valor == null ? "-" : "R$ " + num(valor, 2);
    }

    public String num(Object valor, int casas) {
        if (valor == null) {
            return "-";
        }
        String padrao = casas == 0 ? "#,##0" : "#,##0." + "0".repeat(casas);
        return new DecimalFormat(padrao, BR).format(new BigDecimal(valor.toString()));
    }

    public String pct(Object valor) {
        return valor == null ? "-" : num(valor, 1) + "%";
    }

    /** Variação com sinal: +12,5% ou -3,0%. */
    public String variacao(Object valor) {
        if (valor == null) {
            return "-";
        }
        BigDecimal v = new BigDecimal(valor.toString());
        return (v.signum() > 0 ? "+" : "") + num(v, 1) + "%";
    }

    /**
     * Célula de tabela genérica: decimais no padrão brasileiro (15.658,00) e o resto como veio.
     * Inteiros ficam sem separador de milhar para não confundir números de atendimento e mesa.
     */
    public String celula(Object valor) {
        if (valor == null) {
            return "-";
        }
        if (valor instanceof BigDecimal b && b.scale() > 0) {
            return num(b, b.scale());
        }
        if (valor instanceof Double || valor instanceof Float) {
            return num(valor, 2);
        }
        return valor.toString();
    }

    /** "2026-09-19" -> "19/09/2026". */
    public String data(Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return "-";
        }
        if (valor instanceof LocalDateTime dt) {
            return dt.format(DATA_HORA);
        }
        return LocalDate.parse(valor.toString().substring(0, 10)).format(DATA);
    }
}
