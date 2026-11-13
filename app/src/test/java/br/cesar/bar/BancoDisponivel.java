package br.cesar.bar;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Condição dos testes de integração: só rodam se o banco "bar" (criado pelo
 * scripts/rodar_tudo.ps1) estiver acessível. Usa as mesmas variáveis da aplicação.
 */
public final class BancoDisponivel {

    private static Boolean disponivel;

    private BancoDisponivel() {
    }

    public static synchronized boolean ok() {
        if (disponivel == null) {
            String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/bar");
            String usuario = System.getenv().getOrDefault("DB_USER", "postgres");
            String senha = System.getenv().getOrDefault("DB_PASSWORD", "bd2026");
            DriverManager.setLoginTimeout(3);
            try (Connection c = DriverManager.getConnection(url, usuario, senha)) {
                disponivel = c.isValid(2);
            } catch (Exception e) {
                disponivel = false;
            }
        }
        return disponivel;
    }
}
