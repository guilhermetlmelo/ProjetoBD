package br.cesar.bar.controller;

import br.cesar.bar.model.Funcionario;
import br.cesar.bar.repository.FuncionarioRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
public class FuncionarioController {

    private final FuncionarioRepository funcionarios;

    public FuncionarioController(FuncionarioRepository funcionarios) {
        this.funcionarios = funcionarios;
    }

    /** Lista (com filtro por cargo) + formulário. Com ?editar=cpf o formulário vem preenchido. */
    @GetMapping("/funcionarios")
    public String lista(@RequestParam(required = false) String editar,
                        @RequestParam(required = false) String cargo,
                        Model model) {
        Funcionario form = editar == null ? null : funcionarios.buscar(editar).orElse(null);
        model.addAttribute("edicao", form != null);
        model.addAttribute("form", form != null ? form
                : new Funcionario("", "", "", "", "", "", funcionarios.proximaMatricula(), "", null, "", null, "", 0));
        model.addAttribute("funcionarios", funcionarios.listar(cargo));
        model.addAttribute("cargos", funcionarios.cargos());
        model.addAttribute("supervisores", funcionarios.supervisores());
        model.addAttribute("cargoFiltro", cargo == null ? "" : cargo);
        model.addAttribute("ativo", "funcionarios");
        return "funcionarios";
    }

    @PostMapping("/funcionarios/salvar")
    public String salvar(@RequestParam String cpf,
                         @RequestParam String nome,
                         @RequestParam(defaultValue = "") String rua,
                         @RequestParam(defaultValue = "") String numero,
                         @RequestParam(defaultValue = "") String bairro,
                         @RequestParam(defaultValue = "") String cep,
                         @RequestParam String matricula,
                         @RequestParam String cargo,
                         @RequestParam BigDecimal salario,
                         @RequestParam(defaultValue = "") String cpfSupervisor,
                         @RequestParam(defaultValue = "") String telefones,
                         @RequestParam(defaultValue = "false") boolean edicao,
                         RedirectAttributes ra) {
        Funcionario f = new Funcionario(cpf.replaceAll("\\D", ""), nome.trim(), vazioParaNulo(rua),
                vazioParaNulo(numero), vazioParaNulo(bairro), vazioParaNulo(cep), matricula.trim().toUpperCase(),
                cargo.trim(), salario, vazioParaNulo(cpfSupervisor), null, telefones, 0);
        try {
            if (edicao) {
                funcionarios.atualizar(f);
                ra.addFlashAttribute("sucesso", "Funcionário " + f.nome() + " alterado.");
            } else {
                funcionarios.inserir(f);
                ra.addFlashAttribute("sucesso", "Funcionário " + f.nome() + " inserido.");
            }
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
            return edicao ? "redirect:/funcionarios?editar=" + f.cpf() : "redirect:/funcionarios";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/funcionarios?editar=" + f.cpf();
        }
        return "redirect:/funcionarios";
    }

    @PostMapping("/funcionarios/{cpf}/excluir")
    public String excluir(@PathVariable String cpf, RedirectAttributes ra) {
        try {
            int subordinados = funcionarios.excluir(cpf);
            String msg = "Funcionário " + cpf + " excluído.";
            if (subordinados > 0) {
                msg += " " + subordinados + " funcionário(s) ficaram sem supervisor (ON DELETE SET NULL): "
                        + "use o procedimento de redistribuição na tela Rotinas do banco.";
            }
            ra.addFlashAttribute("sucesso", msg);
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/funcionarios";
    }

    private static String vazioParaNulo(String valor) {
        return valor.isBlank() ? null : valor.trim();
    }
}
