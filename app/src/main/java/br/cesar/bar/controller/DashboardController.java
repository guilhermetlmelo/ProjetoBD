package br.cesar.bar.controller;

import br.cesar.bar.estatistica.TesteT;
import br.cesar.bar.repository.DashboardRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class DashboardController {

    private final DashboardRepository dashboard;

    public DashboardController(DashboardRepository dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("ind", dashboard.indicadores());
        model.addAttribute("estat", dashboard.estatisticasDescritivas());
        model.addAttribute("porCategoria", dashboard.faturamentoPorCategoria());
        model.addAttribute("porDia", dashboard.faturamentoPorDia());
        model.addAttribute("porHora", dashboard.itensPorHora());
        model.addAttribute("porMetodo", dashboard.pagamentosPorMetodo());
        model.addAttribute("topProdutos", dashboard.produtosMaisVendidos());
        model.addAttribute("porLocalizacao", dashboard.atendimentosPorLocalizacao());
        model.addAttribute("distribuicao", dashboard.distribuicaoValorAtendimento());
        model.addAttribute("larguraFaixa", DashboardRepository.LARGURA_FAIXA);

        Map<String, Object> pearson = dashboard.permanenciaValorCorrelacao();
        double t = ((Number) pearson.get("t")).doubleValue();
        int gl = ((Number) pearson.get("gl")).intValue();
        double p = TesteT.pValorBilateral(t, gl);
        model.addAttribute("pearson", pearson);
        model.addAttribute("pValor", p);
        model.addAttribute("significativo", p < 0.05);
        model.addAttribute("pearsonPontos", dashboard.permanenciaValorPontos());

        model.addAttribute("ativo", "dashboard");
        return "dashboard";
    }
}
