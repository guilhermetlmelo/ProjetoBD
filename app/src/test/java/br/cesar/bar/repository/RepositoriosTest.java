package br.cesar.bar.repository;

import br.cesar.bar.TesteDeIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Ramos dos repositórios que as telas não alcançam (a tela valida antes de chamar). */
@TesteDeIntegracao
class RepositoriosTest {

    @Autowired
    private TabelaRepository tabelas;

    @Autowired
    private RotinaRepository rotinas;

    @Test
    void tabelaDesconhecidaNaoGeraSql() {
        assertThat(tabelas.conteudo("pg_user")).isEmpty();
        assertThat(tabelas.descricao("PESSOA")).isNull();
    }

    @Test
    void codigoDeRotinaInexistente() {
        assertThat(rotinas.codigoFonte("nao_existe")).contains("rodar_tudo.ps1");
    }
}
