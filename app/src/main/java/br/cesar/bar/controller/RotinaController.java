package br.cesar.bar.controller;

import br.cesar.bar.repository.ProdutoRepository;
import br.cesar.bar.repository.RotinaRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tela "Rotinas do banco": executa as funções, os procedimentos e os triggers da Etapa 05 e
 * mostra o efeito de cada um. As mensagens voltam para a seção que fez a ação (atributo "secao").
 */
@Controller
public class RotinaController {

    private final RotinaRepository rotinas;
    private final ProdutoRepository produtos;

    public RotinaController(RotinaRepository rotinas, ProdutoRepository produtos) {
        this.rotinas = rotinas;
        this.produtos = produtos;
    }

    @GetMapping("/rotinas")
    public String tela(@RequestParam(required = false) Integer conta, Model model) {
        // funções
        model.addAttribute("conta", conta);
        if (conta != null) {
            try {
                model.addAttribute("funcao", rotinas.funcoesConta(conta));
            } catch (CannotGetJdbcConnectionException e) {
                throw e;
            } catch (DataAccessException e) {
                model.addAttribute("erroFuncao", ErrosBanco.mensagem(e));
            }
        }
        model.addAttribute("porSituacao", rotinas.contasPorSituacao());
        model.addAttribute("pendentes", rotinas.contasPendentes());

        // procedimento 1 + trigger 1
        model.addAttribute("categorias", produtos.categorias());
        model.addAttribute("precoMedio", rotinas.precoMedioPorCategoria());
        model.addAttribute("logPrecos", rotinas.logPrecos());
        model.addAttribute("totalLogs", rotinas.totalLogs());

        // procedimento 2
        model.addAttribute("equipes", rotinas.equipePorGerente());
        model.addAttribute("gerentes", rotinas.gerentes());
        model.addAttribute("semSupervisor", rotinas.semSupervisor());

        // trigger 2
        model.addAttribute("contasTeste", rotinas.contasParaTeste());
        model.addAttribute("produtos", rotinas.produtos());

        Map<String, String> fontes = new LinkedHashMap<>();
        for (String nome : List.of("fn_valor_conta", "fn_situacao_conta", "sp_reajustar_preco_categoria",
                "fn_trg_log_preco_produto", "sp_redistribuir_sem_supervisor", "fn_trg_pedido_conta_fechada")) {
            fontes.put(nome, rotinas.codigoFonte(nome));
        }
        model.addAttribute("fontes", fontes);

        model.addAttribute("ativo", "rotinas");
        return "rotinas";
    }

    @PostMapping("/rotinas/reajuste")
    public String reajuste(@RequestParam String categoria, @RequestParam BigDecimal percentual,
                           RedirectAttributes ra) {
        ra.addFlashAttribute("secao", "reajuste");
        try {
            List<String> avisos = rotinas.reajustarCategoria(categoria, percentual);
            ra.addFlashAttribute("avisos", avisos);
            ra.addFlashAttribute("sucesso", "CALL sp_reajustar_preco_categoria('" + categoria + "', " + percentual
                    + ") executado. Cada preço alterado foi registrado pelo trigger trg_log_preco_produto.");
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/rotinas#reajuste";
    }

    @PostMapping("/rotinas/simular-saida")
    public String simularSaida(@RequestParam String gerente, RedirectAttributes ra) {
        ra.addFlashAttribute("secao", "redistribuir");
        try {
            int qtd = rotinas.simularSaidaGerente(gerente);
            ra.addFlashAttribute("sucesso", qtd + " funcionário(s) ficaram sem supervisor. Agora execute o "
                    + "procedimento para redistribuí-los.");
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/rotinas#redistribuir";
    }

    @PostMapping("/rotinas/redistribuir")
    public String redistribuir(RedirectAttributes ra) {
        ra.addFlashAttribute("secao", "redistribuir");
        try {
            ra.addFlashAttribute("avisos", rotinas.redistribuir());
            ra.addFlashAttribute("sucesso", "CALL sp_redistribuir_sem_supervisor() executado.");
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/rotinas#redistribuir";
    }

    @PostMapping("/rotinas/testar-pedido")
    public String testarPedido(@RequestParam int atendimento, @RequestParam int produto,
                               @RequestParam(defaultValue = "1") int quantidade, RedirectAttributes ra) {
        ra.addFlashAttribute("secao", "trigger");
        try {
            String status = rotinas.testarPedido(atendimento, produto, quantidade);
            ra.addFlashAttribute("sucesso", "Pedido aceito: a conta " + atendimento + " está " + status
                    + ", então o trigger deixou passar. (Teste desfeito com ROLLBACK, nada foi gravado.)");
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/rotinas#trigger";
    }
}
