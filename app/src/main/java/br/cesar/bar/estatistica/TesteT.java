package br.cesar.bar.estatistica;

/**
 * P-valor bilateral da distribuição t de Student, usado no teste de significância da
 * correlação de Pearson (H0: rho = 0). O PostgreSQL calcula r e t; aqui só se obtém o p-valor:
 *
 *   p = I_x(gl/2, 1/2),  com x = gl / (gl + t²)
 *
 * onde I_x é a função beta incompleta regularizada (fração contínua, Numerical Recipes).
 */
public final class TesteT {

    private TesteT() {
    }

    public static double pValorBilateral(double t, int gl) {
        if (gl <= 0 || Double.isNaN(t)) {
            return Double.NaN;
        }
        if (Double.isInfinite(t)) {
            return 0.0;
        }
        double x = gl / (gl + t * t);
        return betaIncompletaRegularizada(gl / 2.0, 0.5, x);
    }

    private static double betaIncompletaRegularizada(double a, double b, double x) {
        if (x <= 0) {
            return 0.0;
        }
        if (x >= 1) {
            return 1.0;
        }
        double bt = Math.exp(lnGama(a + b) - lnGama(a) - lnGama(b) + a * Math.log(x) + b * Math.log(1 - x));
        if (x < (a + 1) / (a + b + 2)) {
            return bt * fracaoContinua(a, b, x) / a;
        }
        return 1 - bt * fracaoContinua(b, a, 1 - x) / b;
    }

    private static final int MAX_ITERACOES = 300;
    private static final double EPS = 1e-15;
    private static final double MINIMO = 1e-300;

    private static double fracaoContinua(double a, double b, double x) {
        return fracaoContinua(a, b, x, MAX_ITERACOES);
    }

    /** Fração contínua (método de Lentz). Visível no pacote para o teste exercitar o limite de iterações. */
    static double fracaoContinua(double a, double b, double x, int maxIter) {
        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 / naoZero(1 - qab * x / qap);
        double h = d;
        for (int m = 1; m <= maxIter; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 / naoZero(1 + aa * d);
            c = naoZero(1 + aa / c);
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 / naoZero(1 + aa * d);
            c = naoZero(1 + aa / c);
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < EPS) {
                break;
            }
        }
        return h;
    }

    /** Evita divisão por zero na fração contínua trocando valores ~0 por um número minúsculo. */
    static double naoZero(double v) {
        return Math.abs(v) < MINIMO ? MINIMO : v;
    }

    /** ln Γ(x) pela aproximação de Lanczos. */
    private static double lnGama(double x) {
        final double[] coef = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double serie = 1.000000000190015;
        for (double c : coef) {
            serie += c / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * serie / x);
    }
}
