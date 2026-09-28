package br.cesar.bar.controller;

import br.cesar.bar.model.ResultadoTabela;
import br.cesar.bar.repository.TabelaRepository;
import org.springframework.http.HttpStatus;
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
        ResultadoTabela resultado = tabelas.conteudo(nome)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("nomes", tabelas.nomes());
        model.addAttribute("tabela", nome.toUpperCase());
        model.addAttribute("resultado", resultado);
        model.addAttribute("ativo", "tabelas");
        return "tabelas";
    }
}
