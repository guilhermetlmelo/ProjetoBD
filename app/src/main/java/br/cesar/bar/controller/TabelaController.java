package br.cesar.bar.controller;

import br.cesar.bar.repository.TabelaRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class TabelaController {

    private final TabelaRepository tabelas;

    public TabelaController(TabelaRepository tabelas) {
        this.tabelas = tabelas;
    }

    @GetMapping("/tabelas")
    public String lista() {
        return "redirect:/tabelas/" + tabelas.nomes().iterator().next();
    }

    @GetMapping("/tabelas/{nome}")
    public String conteudo(@PathVariable String nome, Model model) {
        if (!tabelas.existe(nome)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("nomes", tabelas.nomes());
        model.addAttribute("visoes", tabelas.visoes());
        model.addAttribute("tabela", nome.toUpperCase());
        model.addAttribute("descricao", tabelas.descricao(nome));
        try {
            // A visão pode não existir se o banco foi criado antes da Etapa 04.
            model.addAttribute("resultado", tabelas.conteudo(nome).orElseThrow());
        } catch (CannotGetJdbcConnectionException e) {
            throw e; // banco fora do ar: ConexaoAdvice mostra a página de erro de conexão
        } catch (DataAccessException e) {
            String dica = tabelas.descricao(nome) == null ? ""
                    : " (rode scripts/rodar_tudo.ps1 para criar as visões da Etapa 04)";
            model.addAttribute("erro", ErrosBanco.mensagem(e) + dica);
        }
        model.addAttribute("ativo", "tabelas");
        return "tabelas";
    }
}
