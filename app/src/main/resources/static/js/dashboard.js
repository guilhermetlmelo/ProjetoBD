// Gráficos do dashboard. Os dados chegam prontos em window.DADOS (ver dashboard.html),
// vindos do DashboardRepository já com os filtros aplicados.
(function () {
    'use strict';

    var G = window.Graficos;
    var D = window.DADOS || {};
    if (!G || !G.disponivel) return;

    function campo(lista, nome) { return lista.map(function (d) { return d[nome]; }); }

    // ---------------------------------------------------------------- evolução semanal
    // A tendência é a reta de mínimos quadrados y = a + b·x, com x = 1, 2, 3... (uma por semana).
    function tendencia(valores) {
        var n = valores.length;
        if (n < 2) return null;
        var sx = 0, sy = 0, sxy = 0, sxx = 0;
        valores.forEach(function (y, i) { var x = i + 1; sx += x; sy += y; sxy += x * y; sxx += x * x; });
        var b = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        var a = (sy - b * sx) / n;
        return { a: a, b: b, pontos: valores.map(function (y, i) { return a + b * (i + 1); }) };
    }

    var metricas = {
        faturamento: { nome: 'Faturamento', formato: G.reais, eixo: G.reaisCurto, unidade: function (b) { return G.reais(Math.abs(b)) + ' por semana'; } },
        atendimentos: { nome: 'Atendimentos', formato: G.inteiro, eixo: G.inteiro, unidade: function (b) { return G.decimal(Math.abs(b), 1) + ' atendimento(s) por semana'; } },
        ticket: { nome: 'Ticket médio', formato: G.reais, eixo: G.reaisCurto, unidade: function (b) { return G.reais(Math.abs(b)) + ' por semana'; } }
    };

    function desenharSemanal(chave) {
        var m = metricas[chave];
        var semanal = D.semanal || [];
        var valores = campo(semanal, chave).map(Number);
        var t = tendencia(valores);
        var series = [{ nome: m.nome, valores: valores }];
        if (t) series.push({ nome: 'Tendência linear', valores: t.pontos, tracejada: true, cor: G.cor.texto });
        G.linhas('g-semanal', campo(semanal, 'rotulo').map(function (r) { return 'Sem. ' + r; }), series,
            { formato: m.formato, formatoEixo: m.eixo });

        var nota = document.getElementById('nota-semanal');
        if (nota && t) {
            nota.textContent = 'Cada ponto é uma semana (segunda a domingo, rótulo = segunda-feira). '
                + 'Tendência linear por mínimos quadrados: ' + (t.b >= 0 ? 'alta' : 'queda') + ' de '
                + m.unidade(t.b) + '.';
        }
    }

    document.querySelectorAll('.seletor button').forEach(function (botao) {
        botao.addEventListener('click', function () {
            document.querySelectorAll('.seletor button').forEach(function (b) { b.classList.remove('ativo'); });
            botao.classList.add('ativo');
            desenharSemanal(botao.dataset.metrica);
        });
    });
    desenharSemanal('faturamento');

    // ---------------------------------------------------------------- histograma
    var hist = D.histograma || [];
    G.barras('g-histograma', campo(hist, 'rotulo'), [{ nome: 'Atendimentos', valores: campo(hist, 'valor') }], {
        colado: true,
        formato: function (v) { return G.inteiro(v) + ' atendimento(s)'; },
        formatoEixo: function (v) { return Number.isInteger(v) ? G.inteiro(v) : ''; },
        extra: function (i) {
            return [G.decimal(hist[i].relativa, 1) + '% do total', 'acumulado: ' + hist[i].acumulada];
        }
    });

    // ---------------------------------------------------------------- composição
    var cat = D.porCategoria || [];
    G.pizza('g-categoria', campo(cat, 'rotulo'), campo(cat, 'valor'), { formato: G.reais });

    var met = D.porMetodo || [];
    G.pizza('g-metodo', campo(met, 'rotulo'), campo(met, 'valor'), { formato: G.reais, rosca: false });

    // ---------------------------------------------------------------- dia da semana
    var sem = D.porDiaSemana || [];
    G.barras('g-semana', campo(sem, 'rotulo'), [
        { nome: 'Média', valores: campo(sem, 'media') },
        { nome: 'Mediana', valores: campo(sem, 'mediana') }
    ], {
        formato: G.reais,
        formatoEixo: G.reaisCurto
    });
    // tooltip extra com o movimento do dia (fora da série para não repetir em cada barra)
    (function () {
        var grafico = Chart.getChart('g-semana');
        if (!grafico) return;
        grafico.options.plugins.tooltip.callbacks.footer = function (itens) {
            var d = sem[itens[0].dataIndex];
            return d.atendimentos + ' atendimento(s), ' + G.decimal(d.por_noite, 1) + ' por noite';
        };
        grafico.update();
    })();

    // ---------------------------------------------------------------- radar
    var ca = D.categoriaArea || [];
    var categorias = [];
    var areas = [];
    ca.forEach(function (d) {
        if (categorias.indexOf(d.categoria) < 0) categorias.push(d.categoria);
        if (areas.indexOf(d.area) < 0) areas.push(d.area);
    });
    categorias.sort();
    G.radar('g-radar', categorias, areas.map(function (area) {
        return {
            nome: area,
            valores: categorias.map(function (c) {
                var linha = ca.find(function (d) { return d.area === area && d.categoria === c; });
                return linha ? linha.pct : 0;
            })
        };
    }), { formato: function (v) { return G.decimal(v, 0) + '%'; } });

    // ---------------------------------------------------------------- produtos e horário
    var top = D.topProdutos || [];
    G.barras('g-produtos', campo(top, 'rotulo'), [{ nome: 'Itens', valores: campo(top, 'valor') }], {
        horizontal: true,
        formato: function (v) { return G.inteiro(v) + ' itens'; },
        formatoEixo: G.inteiro,
        extra: function (i) { return [G.reais(top[i].faturamento) + ' faturados']; }
    });

    var hora = D.porHora || [];
    G.barras('g-hora', campo(hora, 'rotulo'), [{ nome: 'Itens', valores: campo(hora, 'valor') }], {
        formato: function (v) { return G.inteiro(v) + ' itens'; },
        formatoEixo: G.inteiro
    });

    // ---------------------------------------------------------------- dispersão (Pearson)
    var pontos = D.pearsonPontos || [];
    var r = D.pearson || {};
    if (pontos.length && r.inclinacao !== null && document.getElementById('g-pearson')) {
        var a = Number(r.intercepto), b = Number(r.inclinacao);
        var x0 = Number(r.x_min), x1 = Number(r.x_max);
        G.desenhar('g-pearson', {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Atendimentos',
                    data: pontos.map(function (p) { return { x: Number(p.x), y: Number(p.y), id: p.id }; }),
                    backgroundColor: G.series[0],
                    borderColor: G.cor.superficie,
                    borderWidth: 1,
                    pointRadius: 4,
                    pointHoverRadius: 7
                }, {
                    type: 'line',
                    label: 'Reta de regressão',
                    data: [{ x: x0, y: a + b * x0 }, { x: x1, y: a + b * x1 }],
                    borderColor: G.cor.texto,
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
                        title: { display: true, text: 'Tempo de permanência (minutos)', color: G.cor.texto },
                        grid: { color: G.cor.grade },
                        border: { color: G.cor.eixo },
                        ticks: { color: G.cor.muted }
                    },
                    y: {
                        beginAtZero: true,
                        title: { display: true, text: 'Valor gasto (R$)', color: G.cor.texto },
                        grid: { color: G.cor.grade },
                        border: { display: false },
                        ticks: { color: G.cor.muted, callback: G.reaisCurto }
                    }
                },
                interaction: { mode: 'nearest', intersect: true },
                plugins: {
                    legend: {
                        display: true, position: 'top', align: 'start',
                        labels: { color: G.cor.texto, usePointStyle: true, boxWidth: 8, boxHeight: 8 }
                    },
                    tooltip: {
                        filter: function (item) { return item.datasetIndex === 0; },
                        callbacks: {
                            title: function (itens) { return 'Atendimento ' + itens[0].raw.id; },
                            label: function (ctx) {
                                return [G.inteiro(ctx.raw.x) + ' min', G.reais(ctx.raw.y)];
                            }
                        }
                    }
                }
            }
        });
    }
})();
