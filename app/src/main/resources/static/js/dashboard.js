// Gráficos do dashboard. Os dados chegam prontos em window.DADOS (ver dashboard.html),
// cada item no formato { rotulo, valor, ... } vindo do DashboardRepository.
(function () {
    'use strict';

    if (typeof Chart === 'undefined') {
        document.querySelectorAll('.grafico').forEach(function (el) {
            el.innerHTML = '<p class="nota">Não foi possível carregar a biblioteca de gráficos (sem internet?). '
                + 'Os números continuam disponíveis nas tabelas.</p>';
        });
        return;
    }

    var css = getComputedStyle(document.documentElement);
    function token(nome) { return css.getPropertyValue(nome).trim(); }

    var cor = {
        serie: token('--series-1'),
        texto: token('--text-2'),
        muted: token('--muted'),
        grade: token('--grid'),
        eixo: token('--axis'),
        superficie: token('--surface')
    };

    var reais = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    var inteiro = new Intl.NumberFormat('pt-BR');

    Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", sans-serif';
    Chart.defaults.color = cor.texto;
    Chart.defaults.plugins.legend.display = false; // uma série por gráfico: o título já a nomeia
    Chart.defaults.plugins.tooltip.displayColors = false;
    Chart.defaults.maintainAspectRatio = false;

    function eixos(horizontal, formatoValor) {
        var valor = {
            beginAtZero: true,
            grid: { color: cor.grade },
            border: { display: false },
            ticks: { color: cor.muted, maxRotation: 0, callback: function (v) { return formatoValor(v); } }
        };
        var categoria = {
            grid: { display: false },
            border: { color: cor.eixo },
            ticks: { color: cor.texto, autoSkip: false }
        };
        return horizontal ? { x: valor, y: categoria } : { x: categoria, y: valor };
    }

    function compactoReais(v) {
        return 'R$ ' + inteiro.format(v);
    }

    /** Barras de uma série só. opcoes: horizontal, formato, tooltipExtra, colado (histograma). */
    function barras(id, dados, opcoes) {
        var el = document.getElementById(id);
        if (!el) return;
        var formato = opcoes.formato || function (v) { return inteiro.format(v); };
        new Chart(el, {
            type: 'bar',
            data: {
                labels: dados.map(function (d) { return d.rotulo; }),
                datasets: [{
                    data: dados.map(function (d) { return Number(d.valor); }),
                    backgroundColor: cor.serie,
                    borderColor: cor.superficie,
                    borderWidth: opcoes.colado ? { left: 1, right: 1 } : 0,
                    borderRadius: 4,          // só na ponta: o lado da base fica reto (borderSkipped)
                    borderSkipped: 'start',
                    barPercentage: opcoes.colado ? 1 : 0.7,
                    categoryPercentage: opcoes.colado ? 1 : 0.8,
                    maxBarThickness: opcoes.colado ? undefined : 48
                }]
            },
            options: {
                indexAxis: opcoes.horizontal ? 'y' : 'x',
                scales: eixos(opcoes.horizontal, opcoes.formatoEixo || formato),
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: function (ctx) {
                                var linhas = [formato(ctx.raw)];
                                if (opcoes.tooltipExtra) linhas = linhas.concat(opcoes.tooltipExtra(dados[ctx.dataIndex]));
                                return linhas;
                            }
                        }
                    }
                }
            }
        });
    }

    function linha(id, dados, formato) {
        var el = document.getElementById(id);
        if (!el) return;
        new Chart(el, {
            type: 'line',
            data: {
                labels: dados.map(function (d) { return d.rotulo; }),
                datasets: [{
                    data: dados.map(function (d) { return Number(d.valor); }),
                    borderColor: cor.serie,
                    backgroundColor: cor.serie,
                    borderWidth: 2,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBorderColor: cor.superficie,
                    pointBorderWidth: 2,
                    tension: 0
                }]
            },
            options: {
                scales: eixos(false, compactoReais),
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    tooltip: { callbacks: { label: function (ctx) { return formato(ctx.raw); } } }
                }
            }
        });
    }

    /** Dispersão (um ponto por atendimento) + reta de regressão y = a + b·x calculada no SQL. */
    function dispersao(id, pontos, resumo) {
        var el = document.getElementById(id);
        if (!el || !pontos.length) return;
        var a = Number(resumo.intercepto), b = Number(resumo.inclinacao);
        var x0 = Number(resumo.x_min), x1 = Number(resumo.x_max);
        new Chart(el, {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Atendimentos',
                    data: pontos.map(function (p) { return { x: Number(p.x), y: Number(p.y), id: p.id }; }),
                    backgroundColor: cor.serie,
                    borderColor: cor.superficie,
                    borderWidth: 2,
                    pointRadius: 5,
                    pointHoverRadius: 7
                }, {
                    type: 'line',
                    label: 'Reta de regressão',
                    data: [{ x: x0, y: a + b * x0 }, { x: x1, y: a + b * x1 }],
                    borderColor: cor.texto,
                    borderWidth: 2,
                    borderDash: [6, 4],
                    pointStyle: 'line',
                    pointRadius: 0,
                    pointHoverRadius: 0
                }]
            },
            options: {
                scales: {
                    x: {
                        type: 'linear',
                        title: { display: true, text: 'Tempo de permanência (minutos)', color: cor.texto },
                        grid: { color: cor.grade },
                        border: { color: cor.eixo },
                        ticks: { color: cor.muted }
                    },
                    y: {
                        beginAtZero: true,
                        title: { display: true, text: 'Valor gasto (R$)', color: cor.texto },
                        grid: { color: cor.grade },
                        border: { display: false },
                        ticks: { color: cor.muted, callback: function (v) { return compactoReais(v); } }
                    }
                },
                interaction: { mode: 'nearest', intersect: true },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                        align: 'start',
                        labels: { color: cor.texto, usePointStyle: true, boxWidth: 8, boxHeight: 8 }
                    },
                    tooltip: {
                        filter: function (item) { return item.datasetIndex === 0; },
                        callbacks: {
                            title: function (itens) { return 'Atendimento ' + itens[0].raw.id; },
                            label: function (ctx) {
                                return [inteiro.format(ctx.raw.x) + ' min', reais.format(ctx.raw.y)];
                            }
                        }
                    }
                }
            }
        });
    }

    var D = window.DADOS || {};
    var formatoReais = function (v) { return reais.format(v); };

    barras('g-histograma', D.histograma || [], {
        colado: true,
        formato: function (v) { return inteiro.format(v) + ' atendimento(s)'; },
        formatoEixo: function (v) { return Number.isInteger(v) ? inteiro.format(v) : ''; },
        tooltipExtra: function (d) { return [d.relativa + '% do total', 'acumulado: ' + d.acumulada]; }
    });

    dispersao('g-pearson', D.pearsonPontos || [], D.pearson || {});

    linha('g-dia', D.porDia || [], formatoReais);

    barras('g-hora', D.porHora || [], {
        formato: function (v) { return inteiro.format(v) + ' itens'; },
        formatoEixo: function (v) { return inteiro.format(v); }
    });

    barras('g-categoria', D.porCategoria || [], {
        horizontal: true, formato: formatoReais, formatoEixo: compactoReais
    });

    barras('g-produtos', D.topProdutos || [], {
        horizontal: true,
        formato: function (v) { return inteiro.format(v) + ' itens'; },
        formatoEixo: function (v) { return inteiro.format(v); }
    });

    barras('g-metodo', D.porMetodo || [], {
        horizontal: true, formato: formatoReais, formatoEixo: compactoReais,
        tooltipExtra: function (d) { return [d.quantidade + ' transação(ões)']; }
    });

    barras('g-local', D.porLocalizacao || [], {
        horizontal: true,
        formato: function (v) { return inteiro.format(v) + ' atendimento(s)'; },
        formatoEixo: function (v) { return Number.isInteger(v) ? inteiro.format(v) : ''; }
    });
})();
