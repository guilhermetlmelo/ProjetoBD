package br.cesar.bar.controller;

import br.cesar.bar.model.Produto;
import br.cesar.bar.repository.ProdutoRepository;
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
public class ProdutoController {

    private final ProdutoRepository produtos;

    public ProdutoController(ProdutoRepository produtos) {
        this.produtos = produtos;
    }

    /** Lista + formulário. Com ?editar=codigo o formulário vem preenchido para alteração. */
    @GetMapping("/produtos")
    public String lista(@RequestParam(required = false) Integer editar, Model model) {
        Produto form = editar == null ? null : produtos.buscar(editar).orElse(null);
        model.addAttribute("edicao", form != null);
        model.addAttribute("form", form != null ? form
                : new Produto(produtos.proximoCodigo(), "", null, ""));
        model.addAttribute("produtos", produtos.listar());
        model.addAttribute("categorias", produtos.categorias());
        model.addAttribute("ativo", "produtos");
        return "produtos";
    }

    @PostMapping("/produtos/salvar")
    public String salvar(@RequestParam int codigoProd,
                         @RequestParam String nomeProduto,
                         @RequestParam BigDecimal precoAtual,
                         @RequestParam String categoria,
                         @RequestParam(defaultValue = "false") boolean edicao,
                         RedirectAttributes ra) {
        Produto p = new Produto(codigoProd, nomeProduto.trim(), precoAtual, categoria.trim());
        try {
            if (edicao) {
                produtos.atualizar(p);
                ra.addFlashAttribute("sucesso", "Produto " + codigoProd + " alterado.");
            } else {
                produtos.inserir(p);
                ra.addFlashAttribute("sucesso", "Produto " + codigoProd + " inserido.");
            }
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
            return edicao ? "redirect:/produtos?editar=" + codigoProd : "redirect:/produtos";
        }
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/{codigo}/excluir")
    public String excluir(@PathVariable int codigo, RedirectAttributes ra) {
        try {
            produtos.excluir(codigo);
            ra.addFlashAttribute("sucesso", "Produto " + codigo + " excluído.");
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/produtos";
    }
}
