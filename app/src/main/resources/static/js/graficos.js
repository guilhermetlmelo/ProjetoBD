// Funções de gráfico usadas pelo dashboard, pelas consultas e pela tela de rotinas.
// Cores e fontes vêm das variáveis do style.css, então o modo escuro funciona sozinho.
(function () {
    'use strict';

    var G = window.Graficos = {};
    var instancias = {};

    G.disponivel = typeof Chart !== 'undefined';

    var css = getComputedStyle(document.documentElement);
    function token(nome) { return css.getPropertyValue(nome).trim(); }

    G.cor = {
        texto: token('--text-2'),
        muted: token('--muted'),
        grade: token('--grid'),
        eixo: token('--axis'),
        superficie: token('--surface')
    };
    G.series = [1, 2, 3, 4, 5, 6, 7, 8].map(function (i) { return token('--series-' + i); });

    G.reais = function (v) {
        return Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    };
    G.inteiro = function (v) { return Number(v).toLocaleString('pt-BR'); };
    G.decimal = function (v, casas) {
        return Number(v).toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
    };
    G.reaisCurto = function (v) { return 'R$ ' + G.inteiro(v); };

    if (!G.disponivel) {
        document.querySelectorAll('.grafico').forEach(function (el) {
            el.innerHTML = '<p class="nota">Não foi possível carregar a biblioteca de gráficos (js/vendor/chart.umd.min.js). '
                + 'Os números continuam disponíveis nas tabelas.</p>';
        });
        return;
    }

    Chart.defaults.font.family = token('--font') || 'system-ui, sans-serif';
    Chart.defaults.color = G.cor.texto;
    Chart.defaults.plugins.legend.display = false;
    Chart.defaults.plugins.tooltip.displayColors = false;
    Chart.defaults.maintainAspectRatio = false;
    Chart.defaults.animation.duration = 300;

    function legenda(mostrar, posicao) {
        return {
            display: mostrar,
            position: posicao || 'top',
            align: 'start',
            labels: { color: G.cor.texto, usePointStyle: true, boxWidth: 8, boxHeight: 8, padding: 14 }
        };
    }

    function eixos(horizontal, formato) {
        var valor = {
            beginAtZero: true,
            grid: { color: G.cor.grade },
            border: { display: false },
            ticks: { color: G.cor.muted, maxRotation: 0, callback: function (v) { return formato(v); } }
        };
        var categoria = {
            grid: { display: false },
            border: { color: G.cor.eixo },
            ticks: { color: G.cor.texto, autoSkip: true, maxRotation: horizontal ? 0 : 45 }
        };
        return horizontal ? { x: valor, y: categoria } : { x: categoria, y: valor };
    }

    function desenhar(id, config) {
        var el = document.getElementById(id);
        if (!el) return null;
        if (instancias[id]) instancias[id].destroy();
        instancias[id] = new Chart(el, config);
        return instancias[id];
    }

    /**
     * Barras. series = [{ nome, valores }]. opcoes: horizontal, formato, formatoEixo,
     * colado (histograma), extra(i) -> linhas a mais no tooltip.
     */
    G.barras = function (id, rotulos, series, opcoes) {
        opcoes = opcoes || {};
        var formato = opcoes.formato || G.inteiro;
        var varias = series.length > 1;
        return desenhar(id, {
            type: 'bar',
            data: {
                labels: rotulos,
                datasets: series.map(function (s, i) {
                    return {
                        label: s.nome,
                        data: s.valores.map(Number),
                        backgroundColor: G.series[i % G.series.length],
                        borderColor: G.cor.superficie,
                        borderWidth: opcoes.colado ? { left: 1, right: 1 } : 0,
                        borderRadius: 4,
                        borderSkipped: 'start',
                        barPercentage: opcoes.colado ? 1 : 0.8,
                        categoryPercentage: opcoes.colado ? 1 : 0.8,
                        maxBarThickness: opcoes.colado ? undefined : 44
                    };
                })
            },
            options: {
                indexAxis: opcoes.horizontal ? 'y' : 'x',
                scales: eixos(opcoes.horizontal, opcoes.formatoEixo || formato),
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: legenda(varias),
                    tooltip: {
                        displayColors: varias,
                        callbacks: {
                            label: function (ctx) {
                                var linha = (varias ? ctx.dataset.label + ': ' : '') + formato(ctx.raw);
                                return opcoes.extra ? [linha].concat(opcoes.extra(ctx.dataIndex)) : linha;
                            }
                        }
                    }
                }
            }
        });
    };

    /** Linhas. series = [{ nome, valores, tracejada }]. */
    G.linhas = function (id, rotulos, series, opcoes) {
        opcoes = opcoes || {};
        var formato = opcoes.formato || G.inteiro;
        var varias = series.length > 1;
        return desenhar(id, {
            type: 'line',
            data: {
                labels: rotulos,
                datasets: series.map(function (s, i) {
                    var cor = s.cor || G.series[i % G.series.length];
                    return {
                        label: s.nome,
                        data: s.valores.map(function (v) { return v === null ? null : Number(v); }),
                        borderColor: cor,
                        backgroundColor: cor,
                        borderWidth: 2,
                        borderDash: s.tracejada ? [6, 4] : [],
                        pointRadius: s.tracejada ? 0 : 3,
                        pointHoverRadius: 6,
                        pointBorderColor: G.cor.superficie,
                        pointBorderWidth: 2,
                        spanGaps: true,
                        tension: 0
                    };
                })
            },
            options: {
                scales: eixos(false, opcoes.formatoEixo || formato),
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: legenda(varias),
                    tooltip: {
                        displayColors: varias,
                        callbacks: {
                            label: function (ctx) {
                                if (ctx.raw === null) return null;
                                return (varias ? ctx.dataset.label + ': ' : '') + formato(ctx.raw);
                            }
                        }
                    }
                }
            }
        });
    };

    /** Pizza ou rosca. Mais de 8 fatias: as menores viram "Outros". */
    G.pizza = function (id, rotulos, valores, opcoes) {
        opcoes = opcoes || {};
        var formato = opcoes.formato || G.inteiro;
        var pares = rotulos.map(function (r, i) { return { r: r, v: Number(valores[i]) }; });
        if (pares.length > 8) {
            pares.sort(function (a, b) { return b.v - a.v; });
            var resto = pares.slice(7).reduce(function (s, p) { return s + p.v; }, 0);
            pares = pares.slice(0, 7).concat([{ r: 'Outros', v: resto }]);
        }
        var total = pares.reduce(function (s, p) { return s + p.v; }, 0);
        return desenhar(id, {
            type: opcoes.rosca === false ? 'pie' : 'doughnut',
            data: {
                labels: pares.map(function (p) { return p.r; }),
                datasets: [{
                    data: pares.map(function (p) { return p.v; }),
                    backgroundColor: pares.map(function (p, i) { return G.series[i]; }),
                    borderColor: G.cor.superficie,
                    borderWidth: 2,
                    hoverOffset: 6
                }]
            },
            options: {
                cutout: opcoes.rosca === false ? 0 : '58%',
                plugins: {
                    legend: legenda(true, 'right'),
                    tooltip: {
                        callbacks: {
                            label: function (ctx) {
                                var pct = total ? (100 * ctx.raw / total) : 0;
                                return ctx.label + ': ' + formato(ctx.raw) + ' (' + G.decimal(pct, 1) + '%)';
                            }
                        }
                    }
                }
            }
        });
    };

    /** Radar. series = [{ nome, valores }] sobre os mesmos eixos (rotulos). */
    G.radar = function (id, rotulos, series, opcoes) {
        opcoes = opcoes || {};
        var formato = opcoes.formato || G.inteiro;
        return desenhar(id, {
            type: 'radar',
            data: {
                labels: rotulos,
                datasets: series.map(function (s, i) {
                    var cor = G.series[i % G.series.length];
                    return {
                        label: s.nome,
                        data: s.valores.map(Number),
                        borderColor: cor,
                        backgroundColor: cor + '22',
                        borderWidth: 2,
                        pointRadius: 3,
                        pointBackgroundColor: cor,
                        pointBorderColor: G.cor.superficie
                    };
                })
            },
            options: {
                scales: {
                    r: {
                        beginAtZero: true,
                        grid: { color: G.cor.grade },
                        angleLines: { color: G.cor.grade },
                        pointLabels: { color: G.cor.texto },
                        // a escala em cima das linhas atrapalha a leitura: o valor exato vem no tooltip
                        ticks: { display: false }
                    }
                },
                plugins: {
                    legend: legenda(series.length > 1),
                    tooltip: {
                        displayColors: true,
                        callbacks: { label: function (ctx) { return ctx.dataset.label + ': ' + formato(ctx.raw); } }
                    }
                }
            }
        });
    };

    G.desenhar = desenhar;
})();
