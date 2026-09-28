package br.cesar.bar.controller;

import br.cesar.bar.model.Consulta;
import br.cesar.bar.repository.ConsultaRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ConsultaController {

    private final ConsultaRepository consultas;

    public ConsultaController(ConsultaRepository consultas) {
        this.consultas = consultas;
    }

    @GetMapping("/consultas")
    public String lista() {
        return "redirect:/consultas/" + consultas.listar().get(0).id();
    }

    @GetMapping("/consultas/{id}")
    public String executar(@PathVariable String id, Model model) {
        Consulta consulta = consultas.buscar(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("consultas", consultas.listar());
        model.addAttribute("consulta", consulta);
        try {
            model.addAttribute("resultado", consultas.executar(consulta));
        } catch (DataAccessException e) {
            model.addAttribute("erro", ErrosBanco.mensagem(e));
        }
        model.addAttribute("ativo", "consultas");
        return "consultas";
    }
}
