// Indicadores e gráfico do resultado da consulta aberta. Os dados vêm em window.CONSULTA
// (ver consultas.html): a configuração do gráfico e as colunas/linhas do resultado.
(function () {
    'use strict';

    var C = window.CONSULTA;
    var G = window.Graficos;
    if (!C || !G) return;

    var g = C.grafico;
    var colunas = C.colunas;
    var linhas = C.linhas;
    var iRotulo = colunas.indexOf(g.rotulo);
    var iValores = g.valores.map(function (v) { return colunas.indexOf(v); });

    function ehDinheiro(nome) { return /R\$|valor|gasto|preco|faturamento/i.test(nome); }
    function formatoDe(nome) { return ehDinheiro(nome) ? G.reais : function (v) { return G.decimal(v, Number.isInteger(Number(v)) ? 0 : 1); }; }

    // --- Agrupamento conforme o modo do gráfico ---
    var rotulos = [];
    var series = [];
    if (g.modo === 'linha') {
        rotulos = linhas.map(function (l) { return String(l[iRotulo]); });
        series = iValores.map(function (iv, k) {
            return { nome: g.valores[k], valores: linhas.map(function (l) { return Number(l[iv]) || 0; }) };
        });
    } else {
        var mapa = {};
        var ordem = [];
        linhas.forEach(function (l) {
            var chave = String(l[iRotulo]);
            if (!(chave in mapa)) { mapa[chave] = iValores.map(function () { return 0; }); mapa[chave].n = 0; ordem.push(chave); }
            mapa[chave].n += 1;
            iValores.forEach(function (iv, k) { mapa[chave][k] += Number(l[iv]) || 0; });
        });
        if (g.modo === 'contagem') {
            ordem.sort(function (a, b) { return mapa[b].n - mapa[a].n; });
            rotulos = ordem;
            series = [{ nome: 'Quantidade', valores: ordem.map(function (k) { return mapa[k].n; }) }];
        } else {
            ordem.sort(function (a, b) { return mapa[b][0] - mapa[a][0]; });
            rotulos = ordem;
            series = g.valores.map(function (nome, k) {
                return { nome: nome, valores: ordem.map(function (c) { return mapa[c][k]; }) };
            });
        }
    }

    // --- Indicadores ---
    var nomeValor = g.modo === 'contagem' ? 'Quantidade' : g.valores[0];
    var formato = g.modo === 'contagem' ? G.inteiro : formatoDe(nomeValor);
    var base = series.length ? series[0].valores : [];
    var total = base.reduce(function (s, v) { return s + v; }, 0);
    var maior = -Infinity, rotuloMaior = '-';
    base.forEach(function (v, i) { if (v > maior) { maior = v; rotuloMaior = rotulos[i]; } });

    var cards = [['Linhas no resultado', G.inteiro(linhas.length), '']];
    if (base.length) {
        if (g.modo === 'contagem') {
            cards.push(['Grupos (' + g.rotulo + ')', G.inteiro(rotulos.length), '']);
            cards.push(['Mais frequente (moda)', rotuloMaior, G.inteiro(maior) + ' linha(s), '
                + G.decimal(100 * maior / total, 1) + '%']);
        } else {
            cards.push(['Total de ' + nomeValor, formato(total), '']);
            cards.push(['Média por ' + g.rotulo.toLowerCase(), formato(total / base.length), '']);
            cards.push(['Maior valor', formato(maior), rotuloMaior]);
        }
    }
    var alvo = document.getElementById('indicadores-consulta');
    if (alvo) {
        alvo.innerHTML = cards.map(function () {
            return '<div class="kpi"><div class="rotulo"></div><div class="valor"></div><div class="extra"></div></div>';
        }).join('');
        alvo.querySelectorAll('.kpi').forEach(function (el, i) {
            el.querySelector('.rotulo').textContent = cards[i][0];
            el.querySelector('.valor').textContent = cards[i][1];
            el.querySelector('.extra').textContent = cards[i][2];
        });
    }

    if (!G.disponivel || !linhas.length || iRotulo < 0) return;

    // --- Gráfico ---
    var limite = g.tipo === 'barh' || g.tipo === 'bar' ? 25 : rotulos.length;
    if (rotulos.length > limite) {
        rotulos = rotulos.slice(0, limite);
        series.forEach(function (s) { s.valores = s.valores.slice(0, limite); });
    }

    if (g.tipo === 'pie' || g.tipo === 'doughnut') {
        G.pizza('g-consulta', rotulos, series[0].valores, { formato: formato, rosca: g.tipo === 'doughnut' });
    } else if (g.tipo === 'line') {
        G.linhas('g-consulta', rotulos, series, { formato: formato });
    } else if (g.tipo === 'radar') {
        G.radar('g-consulta', rotulos, series, { formato: formato });
    } else {
        G.barras('g-consulta', rotulos, series, {
            horizontal: g.tipo === 'barh',
            formato: formato,
            formatoEixo: ehDinheiro(nomeValor) ? G.reaisCurto : G.inteiro
        });
    }
})();
