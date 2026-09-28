package br.cesar.bar.model;

import java.util.List;

/**
 * Resultado genérico de um SELECT: nomes das colunas, se cada coluna é numérica (para alinhar
 * à direita) e as linhas, na ordem do ResultSet.
 */
public record ResultadoTabela(List<String> colunas, List<Boolean> numericas, List<List<Object>> linhas) {
}
