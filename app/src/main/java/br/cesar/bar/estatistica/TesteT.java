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

    private static double fracaoContinua(double a, double b, double x) {
        final int maxIter = 300;
        final double eps = 1e-15;
        final double minimo = 1e-300;

        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 - qab * x / qap;
        if (Math.abs(d) < minimo) {
            d = minimo;
        }
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= maxIter; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < minimo) {
                d = minimo;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < minimo) {
                c = minimo;
            }
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < minimo) {
                d = minimo;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < minimo) {
                c = minimo;
            }
            d = 1 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < eps) {
                break;
            }
        }
        return h;
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
