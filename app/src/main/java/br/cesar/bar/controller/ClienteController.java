package br.cesar.bar.controller;

import br.cesar.bar.model.Cliente;
import br.cesar.bar.repository.ClienteRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class ClienteController {

    private final ClienteRepository clientes;

    public ClienteController(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    /** Lista + formulário. Com ?editar=cpf o formulário vem preenchido para alteração. */
    @GetMapping("/clientes")
    public String lista(@RequestParam(required = false) String editar, Model model) {
        Cliente form = editar == null ? null : clientes.buscar(editar).orElse(null);
        model.addAttribute("edicao", form != null);
        model.addAttribute("form", form != null ? form
                : new Cliente("", "", "", "", "", "", LocalDate.now(), false, ""));
        model.addAttribute("clientes", clientes.listar());
        model.addAttribute("ativo", "clientes");
        return "clientes";
    }

    @PostMapping("/clientes/salvar")
    public String salvar(@RequestParam String cpf,
                         @RequestParam String nome,
                         @RequestParam(defaultValue = "") String rua,
                         @RequestParam(defaultValue = "") String numero,
                         @RequestParam(defaultValue = "") String bairro,
                         @RequestParam(defaultValue = "") String cep,
                         @RequestParam LocalDate dataPrimeiraVisita,
                         @RequestParam(defaultValue = "false") boolean fidelidadeAtiva,
                         @RequestParam(defaultValue = "") String telefones,
                         @RequestParam(defaultValue = "false") boolean edicao,
                         RedirectAttributes ra) {
        Cliente c = new Cliente(cpf.replaceAll("\\D", ""), nome.trim(), vazioParaNulo(rua),
                vazioParaNulo(numero), vazioParaNulo(bairro), vazioParaNulo(cep),
                dataPrimeiraVisita, fidelidadeAtiva, telefones);
        try {
            if (edicao) {
                clientes.atualizar(c);
                ra.addFlashAttribute("sucesso", "Cliente " + c.nome() + " alterado.");
            } else {
                clientes.inserir(c);
                ra.addFlashAttribute("sucesso", "Cliente " + c.nome() + " inserido.");
            }
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
            return edicao ? "redirect:/clientes?editar=" + c.cpf() : "redirect:/clientes";
        }
        return "redirect:/clientes";
    }

    @PostMapping("/clientes/{cpf}/excluir")
    public String excluir(@PathVariable String cpf, RedirectAttributes ra) {
        try {
            clientes.excluir(cpf);
            ra.addFlashAttribute("sucesso", "Cliente " + cpf + " excluído.");
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/clientes";
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
