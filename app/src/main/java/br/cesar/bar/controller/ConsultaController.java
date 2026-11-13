package br.cesar.bar.controller;

import br.cesar.bar.model.Consulta;
import br.cesar.bar.model.Parametro;
import br.cesar.bar.repository.ConsultaRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Executa a consulta com os filtros da URL. Na primeira abertura (sem nenhum filtro na URL)
     * cada filtro usa o seu valor padrão.
     */
    @GetMapping("/consultas/{id}")
    public String executar(@PathVariable String id, @RequestParam Map<String, String> url, Model model) {
        Consulta consulta = consultas.buscar(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        boolean filtrado = consulta.parametros().stream().anyMatch(p -> url.containsKey(p.nome()));
        Map<String, String> valores = new LinkedHashMap<>();
        for (Parametro p : consulta.parametros()) {
            valores.put(p.nome(), filtrado ? url.getOrDefault(p.nome(), "") : p.padrao());
        }

        model.addAttribute("consultas", consultas.listar());
        model.addAttribute("consulta", consulta);
        model.addAttribute("valores", valores);
        model.addAttribute("ativo", "consultas");
        try {
            model.addAttribute("opcoes", consultas.opcoes(consulta));
            model.addAttribute("resultado", consultas.executar(consulta, valores));
        } catch (CannotGetJdbcConnectionException e) {
            throw e;
        } catch (DataAccessException e) {
            model.addAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "consultas";
    }
}
