package br.cesar.bar.controller;

import br.cesar.bar.estatistica.TesteT;
import br.cesar.bar.repository.DashboardRepository;
import br.cesar.bar.repository.DashboardRepository.Filtro;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final DashboardRepository dashboard;

    public DashboardController(DashboardRepository dashboard) {
        this.dashboard = dashboard;
    }

    /**
     * Dashboard com filtros de período, categoria e área. Sem datas na URL, o período é o
     * intervalo inteiro de atendimentos que existe no banco.
     */
    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String inicio,
                            @RequestParam(required = false) String fim,
                            @RequestParam(required = false) String categoria,
                            @RequestParam(required = false) String area,
                            Model model) {
        Map<String, Object> periodo = dashboard.periodoDisponivel();
        if (inicio == null && fim == null) {
            inicio = (String) periodo.get("inicio");
            fim = (String) periodo.get("fim");
        }
        Filtro f = new Filtro(inicio, fim, categoria, area);

        model.addAttribute("filtro", f);
        model.addAttribute("periodo", periodo);
        model.addAttribute("categorias", dashboard.categorias());
        model.addAttribute("areas", dashboard.areas());

        model.addAttribute("ind", dashboard.indicadores(f));
        model.addAttribute("tendencia", dashboard.tendenciaSemanal(f));
        model.addAttribute("estat", dashboard.estatisticasDescritivas(f));

        List<Map<String, Object>> distribuicao = dashboard.distribuicaoValorAtendimento(f);
        model.addAttribute("distribuicao", distribuicao);
        model.addAttribute("classeModal", classeModal(distribuicao));
        model.addAttribute("larguraFaixa", DashboardRepository.LARGURA_FAIXA);

        model.addAttribute("semanal", dashboard.evolucaoSemanal(f));
        model.addAttribute("porCategoria", dashboard.faturamentoPorCategoria(f));
        model.addAttribute("topProdutos", dashboard.produtosMaisVendidos(f));
        model.addAttribute("porMetodo", dashboard.pagamentosPorMetodo(f));
        model.addAttribute("porHora", dashboard.itensPorHora(f));
        model.addAttribute("porDiaSemana", dashboard.porDiaDaSemana(f));
        model.addAttribute("categoriaArea", dashboard.categoriaPorArea(f));

        Map<String, Object> pearson = dashboard.permanenciaValorCorrelacao(f);
        Number t = (Number) pearson.get("t");
        int gl = ((Number) pearson.get("gl")).intValue();
        double p = t == null ? Double.NaN : TesteT.pValorBilateral(t.doubleValue(), gl);
        model.addAttribute("pearson", pearson);
        model.addAttribute("pearsonOk", pearson.get("r") != null && !Double.isNaN(p));
        model.addAttribute("pValor", p);
        model.addAttribute("significativo", p < 0.05);
        model.addAttribute("pearsonPontos", dashboard.permanenciaValorPontos(f));

        model.addAttribute("ativo", "dashboard");
        return "dashboard";
    }

    /** Faixa do histograma com a maior frequência absoluta. */
    private static String classeModal(List<Map<String, Object>> distribuicao) {
        String rotulo = "-";
        long maior = 0;
        for (Map<String, Object> faixa : distribuicao) {
            long freq = ((Number) faixa.get("valor")).longValue();
            if (freq > maior) {
                maior = freq;
                rotulo = (String) faixa.get("rotulo");
            }
        }
        return rotulo;
    }
}
