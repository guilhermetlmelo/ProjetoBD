package br.cesar.bar.controller;

import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Se o PostgreSQL não estiver no ar, mostra uma página explicando o que fazer. */
@ControllerAdvice
public class ConexaoAdvice {

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public String semConexao(CannotGetJdbcConnectionException e, Model model) {
        model.addAttribute("detalhe", e.getMostSpecificCause().getMessage());
        return "erro-conexao";
    }
}
