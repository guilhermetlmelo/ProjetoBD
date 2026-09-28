package br.cesar.bar.controller;

import br.cesar.bar.model.Mesa;
import br.cesar.bar.repository.MesaRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MesaController {

    private final MesaRepository mesas;

    public MesaController(MesaRepository mesas) {
        this.mesas = mesas;
    }

    /** Lista + formulário. Com ?editar=numero o formulário vem preenchido para alteração. */
    @GetMapping("/mesas")
    public String lista(@RequestParam(required = false) Integer editar, Model model) {
        Mesa form = editar == null ? null : mesas.buscar(editar).orElse(null);
        model.addAttribute("edicao", form != null);
        model.addAttribute("form", form != null ? form : new Mesa(mesas.proximoNumero(), null, ""));
        model.addAttribute("mesas", mesas.listar());
        model.addAttribute("localizacoes", mesas.localizacoes());
        model.addAttribute("ativo", "mesas");
        return "mesas";
    }

    @PostMapping("/mesas/salvar")
    public String salvar(@RequestParam int numeroMesa,
                         @RequestParam int capacidade,
                         @RequestParam String localizacao,
                         @RequestParam(defaultValue = "false") boolean edicao,
                         RedirectAttributes ra) {
        Mesa m = new Mesa(numeroMesa, capacidade, localizacao.trim());
        try {
            if (edicao) {
                mesas.atualizar(m);
                ra.addFlashAttribute("sucesso", "Mesa " + numeroMesa + " alterada.");
            } else {
                mesas.inserir(m);
                ra.addFlashAttribute("sucesso", "Mesa " + numeroMesa + " inserida.");
            }
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
            return edicao ? "redirect:/mesas?editar=" + numeroMesa : "redirect:/mesas";
        }
        return "redirect:/mesas";
    }

    @PostMapping("/mesas/{numero}/excluir")
    public String excluir(@PathVariable int numero, RedirectAttributes ra) {
        try {
            mesas.excluir(numero);
            ra.addFlashAttribute("sucesso", "Mesa " + numero + " excluída.");
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/mesas";
    }
}
