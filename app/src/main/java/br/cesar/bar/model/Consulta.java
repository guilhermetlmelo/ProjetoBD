package br.cesar.bar.model;

import java.util.List;

/** Uma das consultas das entregas (etapa "03" ou "04"): o SQL é exibido na tela junto com o resultado. */
public record Consulta(String id, String etapa, String titulo, String descricao, List<String> recursos, String sql) {
}
