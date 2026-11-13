package br.cesar.bar;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Teste de integração contra o PostgreSQL de verdade (funções, procedimentos e triggers só
 * existem lá). @Transactional desfaz tudo no fim de cada teste, então o banco continua igual
 * ao gerado pelo rodar_tudo.ps1. Sem banco no ar, os testes são pulados (não falham).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf(value = "br.cesar.bar.BancoDisponivel#ok", disabledReason = "PostgreSQL com o banco bar não está acessível")
public @interface TesteDeIntegracao {
}
