package br.cesar.bar.controller;

import br.cesar.bar.model.Atendimento;
import br.cesar.bar.repository.AtendimentoRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.IntSupplier;

@Controller
public class AtendimentoController {

    private static final List<String> METODOS = List.of("PIX", "CREDITO", "DEBITO", "DINHEIRO");
    private static final List<String> SITUACOES = List.of("SEM CONSUMO", "EM ABERTO", "PARCIAL", "QUITADA", "PAGO A MAIS");

    private final AtendimentoRepository atendimentos;

    public AtendimentoController(AtendimentoRepository atendimentos) {
        this.atendimentos = atendimentos;
    }

    // ------------------------------------------------------------------ lista e abertura

    @GetMapping("/atendimentos")
    public String lista(@RequestParam(defaultValue = "") String status,
                        @RequestParam(defaultValue = "") String situacao,
                        @RequestParam(defaultValue = "") String inicio,
                        @RequestParam(defaultValue = "") String fim,
                        Model model) {
        List<Atendimento> lista = atendimentos.listar(status, situacao, inicio, fim);
        model.addAttribute("atendimentos", lista);
        model.addAttribute("totalConsumido", lista.stream().map(Atendimento::consumido).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("totalPago", lista.stream().map(Atendimento::pago).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("abertos", lista.stream().filter(a -> "ABERTA".equals(a.status())).count());
        model.addAttribute("filtro", new String[]{status, situacao, inicio, fim});
        model.addAttribute("situacoes", SITUACOES);
        model.addAttribute("proximoId", atendimentos.proximoId());
        model.addAttribute("agora", LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        opcoes(model);
        model.addAttribute("ativo", "atendimentos");
        return "atendimentos";
    }

    @PostMapping("/atendimentos/novo")
    public String abrir(@RequestParam int id,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime abertura,
                        @RequestParam String cliente,
                        @RequestParam String garcom,
                        @RequestParam int mesa,
                        RedirectAttributes ra) {
        try {
            atendimentos.inserir(id, abertura, cliente, garcom, mesa);
            ra.addFlashAttribute("sucesso", "Atendimento " + id + " aberto. Lance os pedidos abaixo.");
            return "redirect:/atendimentos/" + id;
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
            return "redirect:/atendimentos";
        }
    }

    // ------------------------------------------------------------------ detalhe da conta

    @GetMapping("/atendimentos/{id}")
    public String detalhe(@PathVariable int id, Model model) {
        Atendimento a = atendimentos.buscar(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("a", a);
        model.addAttribute("itens", atendimentos.itens(id));
        model.addAttribute("pagamentos", atendimentos.pagamentos(id));
        model.addAttribute("proximoPedido", atendimentos.proximoPedido(id));
        model.addAttribute("produtos", atendimentos.produtos());
        model.addAttribute("metodos", METODOS);
        opcoes(model);
        model.addAttribute("ativo", "atendimentos");
        return "atendimento";
    }

    @PostMapping("/atendimentos/{id}/salvar")
    public String salvar(@PathVariable int id,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime abertura,
                         @RequestParam String cliente,
                         @RequestParam String garcom,
                         @RequestParam int mesa,
                         RedirectAttributes ra) {
        executar(ra, "Dados do atendimento alterados.", () -> atendimentos.atualizar(id, abertura, cliente, garcom, mesa));
        return "redirect:/atendimentos/" + id;
    }

    @PostMapping("/atendimentos/{id}/fechar")
    public String fechar(@PathVariable int id, RedirectAttributes ra) {
        try {
            String situacao = atendimentos.fechar(id);
            if ("EM ABERTO".equals(situacao) || "PARCIAL".equals(situacao)) {
                ra.addFlashAttribute("erro", "A conta não foi fechada: fn_situacao_conta devolveu " + situacao
                        + ". Registre os pagamentos que faltam antes de fechar.");
            } else {
                ra.addFlashAttribute("sucesso", "Conta fechada (situação: " + situacao + "). A partir de agora o "
                        + "trigger trg_pedido_conta_fechada recusa novos pedidos nela.");
            }
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
        return "redirect:/atendimentos/" + id;
    }

    @PostMapping("/atendimentos/{id}/reabrir")
    public String reabrir(@PathVariable int id, RedirectAttributes ra) {
        executar(ra, "Conta reaberta.", () -> atendimentos.reabrir(id));
        return "redirect:/atendimentos/" + id;
    }

    @PostMapping("/atendimentos/{id}/excluir")
    public String excluir(@PathVariable int id, RedirectAttributes ra) {
        try {
            atendimentos.excluir(id);
            ra.addFlashAttribute("sucesso", "Atendimento " + id + " excluído, com os pedidos e pagamentos.");
            return "redirect:/atendimentos";
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
            return "redirect:/atendimentos/" + id;
        }
    }

    // ------------------------------------------------------------------ itens (PEDIDO)

    @PostMapping("/atendimentos/{id}/itens")
    public String adicionarItem(@PathVariable int id,
                                @RequestParam int idPedido,
                                @RequestParam int produto,
                                @RequestParam int quantidade,
                                RedirectAttributes ra) {
        executar(ra, "Item lançado no pedido " + idPedido + ".",
                () -> atendimentos.adicionarItem(id, idPedido, produto, quantidade));
        return "redirect:/atendimentos/" + id + "#itens";
    }

    @PostMapping("/atendimentos/{id}/itens/{pedido}/{produto}/quantidade")
    public String alterarQuantidade(@PathVariable int id, @PathVariable int pedido, @PathVariable int produto,
                                    @RequestParam int quantidade, RedirectAttributes ra) {
        executar(ra, "Quantidade alterada.", () -> atendimentos.alterarQuantidade(id, pedido, produto, quantidade));
        return "redirect:/atendimentos/" + id + "#itens";
    }

    @PostMapping("/atendimentos/{id}/itens/{pedido}/{produto}/excluir")
    public String removerItem(@PathVariable int id, @PathVariable int pedido, @PathVariable int produto,
                              RedirectAttributes ra) {
        remover(ra, "Item removido.", () -> atendimentos.removerItem(id, pedido, produto));
        return "redirect:/atendimentos/" + id + "#itens";
    }

    // ------------------------------------------------------------------ PAGAMENTO_PARCIAL

    @PostMapping("/atendimentos/{id}/pagamentos")
    public String adicionarPagamento(@PathVariable int id, @RequestParam BigDecimal valor,
                                     @RequestParam String metodo, RedirectAttributes ra) {
        executar(ra, "Pagamento registrado.", () -> atendimentos.adicionarPagamento(id, valor, metodo));
        return "redirect:/atendimentos/" + id + "#pagamentos";
    }

    @PostMapping("/atendimentos/{id}/pagamentos/{num}/excluir")
    public String removerPagamento(@PathVariable int id, @PathVariable int num, RedirectAttributes ra) {
        remover(ra, "Pagamento removido.", () -> atendimentos.removerPagamento(id, num));
        return "redirect:/atendimentos/" + id + "#pagamentos";
    }

    // ------------------------------------------------------------------ apoio

    private void opcoes(Model model) {
        model.addAttribute("clientes", atendimentos.clientes());
        model.addAttribute("garcons", atendimentos.garcons());
        model.addAttribute("mesas", atendimentos.mesas());
    }

    /** Remoção que só vale em conta aberta: 0 linhas afetadas = conta fechada (ou registro inexistente). */
    private static void remover(RedirectAttributes ra, String sucesso, IntSupplier acao) {
        if (acao.getAsInt() > 0) {
            ra.addFlashAttribute("sucesso", sucesso);
        } else {
            ra.addFlashAttribute("erro", "Nada foi removido: a conta está fechada (reabra para alterar) "
                    + "ou o registro não existe mais.");
        }
    }

    private static void executar(RedirectAttributes ra, String sucesso, Runnable acao) {
        try {
            acao.run();
            ra.addFlashAttribute("sucesso", sucesso);
        } catch (DataAccessException e) {
            ra.addFlashAttribute("erro", ErrosBanco.mensagem(e));
        }
    }
}
