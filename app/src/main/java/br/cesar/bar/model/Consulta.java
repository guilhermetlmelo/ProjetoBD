package br.cesar.bar.model;

import java.util.List;

/** Uma das consultas da entrega: o SQL é exibido na tela junto com o resultado. */
public record Consulta(String id, String titulo, String descricao, List<String> recursos, String sql) {
}
