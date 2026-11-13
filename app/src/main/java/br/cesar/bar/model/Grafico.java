package br.cesar.bar.model;

import java.util.List;

/**
 * Como desenhar o resultado de uma consulta.
 * tipo: bar, barh (barras horizontais), line, pie, doughnut ou radar.
 * modo: "linha" usa cada linha do resultado como um ponto; "soma" agrupa pela coluna de
 * rótulo somando as colunas de valor; "contagem" conta quantas linhas há em cada rótulo.
 */
public record Grafico(String tipo, String modo, String rotulo, List<String> valores, String titulo) {

    public static Grafico linhas(String tipo, String rotulo, String titulo, String... valores) {
        return new Grafico(tipo, "linha", rotulo, List.of(valores), titulo);
    }

    public static Grafico soma(String tipo, String rotulo, String titulo, String... valores) {
        return new Grafico(tipo, "soma", rotulo, List.of(valores), titulo);
    }

    public static Grafico contagem(String tipo, String rotulo, String titulo) {
        return new Grafico(tipo, "contagem", rotulo, List.of(), titulo);
    }
}
