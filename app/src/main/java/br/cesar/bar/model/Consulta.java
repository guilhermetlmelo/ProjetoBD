package br.cesar.bar.model;

import java.util.List;

/**
 * Uma consulta ou visão mostrada na tela. grupo: "03", "04" (consultas das etapas) ou "visoes".
 * O SQL usa parâmetros nomeados (:nome) que vêm dos filtros da tela.
 */
public record Consulta(String id, String grupo, String titulo, String descricao, List<String> recursos,
                       List<Parametro> parametros, Grafico grafico, String sql) {
}
