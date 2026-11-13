package br.cesar.bar.controller;

import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

/** Traduz erros do PostgreSQL (pelo SQLSTATE) em mensagens para mostrar na tela. */
final class ErrosBanco {

    private ErrosBanco() {
    }

    static String mensagem(DataAccessException e) {
        Throwable causa = e.getMostSpecificCause();
        String detalhe = limpar(causa.getMessage());
        if (causa instanceof SQLException sql && sql.getSQLState() != null) {
            return switch (sql.getSQLState()) {
                case "23505" -> "Já existe um registro com essa chave ou valor único. " + detalhe;
                case "23503" -> "Operação bloqueada pela integridade referencial: o registro é usado "
                        + "em outra tabela (ou referencia um registro que não existe). " + detalhe;
                case "23514" -> "Valor rejeitado por uma restrição CHECK. " + detalhe;
                case "23502" -> "Um campo obrigatório ficou vazio. " + detalhe;
                case "22001" -> "Um valor é maior do que o tamanho da coluna permite. " + detalhe;
                case "P0001" -> "Recusado pelo banco (RAISE EXCEPTION em trigger/procedimento): " + detalhe;
                default -> detalhe;
            };
        }
        return detalhe;
    }

    /** Tira o prefixo "ERROR:" e as linhas de contexto (Where/Onde) que o driver anexa. */
    private static String limpar(String msg) {
        if (msg == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String linha : msg.split("\\R")) {
            String l = linha.trim();
            if (l.isEmpty() || l.startsWith("Where:") || l.startsWith("Onde:")) {
                continue;
            }
            sb.append(sb.isEmpty() ? "" : " ").append(l);
        }
        return sb.toString().replaceFirst("^(ERROR|ERRO):\\s*", "");
    }
}
