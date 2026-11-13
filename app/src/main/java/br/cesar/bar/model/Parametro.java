package br.cesar.bar.model;

import java.util.List;

/**
 * Filtro de uma consulta. O nome é o mesmo do parâmetro nomeado no SQL (:nome).
 * tipo: "data", "lista" (opções fixas ou vindas de um SELECT) ou "numero".
 * Valor vazio significa "sem filtro": o SQL usa (CAST(:nome AS ...) IS NULL OR ...).
 */
public record Parametro(String nome, String rotulo, String tipo, String padrao,
                        List<String> opcoes, String sqlOpcoes) {

    public static Parametro data(String nome, String rotulo, String padrao) {
        return new Parametro(nome, rotulo, "data", padrao, List.of(), null);
    }

    public static Parametro lista(String nome, String rotulo, List<String> opcoes) {
        return new Parametro(nome, rotulo, "lista", "", opcoes, null);
    }

    public static Parametro listaDoBanco(String nome, String rotulo, String sqlOpcoes) {
        return new Parametro(nome, rotulo, "lista", "", List.of(), sqlOpcoes);
    }

    public static Parametro numero(String nome, String rotulo, String padrao) {
        return new Parametro(nome, rotulo, "numero", padrao, List.of(), null);
    }
}
