package br.cesar.bar.repository;

import br.cesar.bar.model.ResultadoTabela;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Converte qualquer ResultSet em ResultadoTabela, lendo os nomes das colunas do metadata. */
final class Resultados {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Set<Integer> TIPOS_NUMERICOS = Set.of(
            Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC, Types.DECIMAL,
            Types.REAL, Types.FLOAT, Types.DOUBLE);

    private Resultados() {
    }

    static final ResultSetExtractor<ResultadoTabela> EXTRATOR = rs -> {
        ResultSetMetaData meta = rs.getMetaData();
        int qtd = meta.getColumnCount();

        List<String> colunas = new ArrayList<>();
        List<Boolean> numericas = new ArrayList<>();
        for (int i = 1; i <= qtd; i++) {
            colunas.add(meta.getColumnLabel(i));
            numericas.add(TIPOS_NUMERICOS.contains(meta.getColumnType(i)));
        }

        List<List<Object>> linhas = new ArrayList<>();
        while (rs.next()) {
            List<Object> linha = new ArrayList<>();
            for (int i = 1; i <= qtd; i++) {
                linha.add(formatar(rs.getObject(i)));
            }
            linhas.add(linha);
        }
        return new ResultadoTabela(colunas, numericas, linhas);
    };

    /** Datas no padrão brasileiro e booleanos como Sim/Nao; números seguem como números. */
    private static Object formatar(Object valor) {
        if (valor instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATA_HORA);
        }
        if (valor instanceof Date d) {
            return d.toLocalDate().format(DATA);
        }
        if (valor instanceof Boolean b) {
            return b ? "Sim" : "Nao";
        }
        return valor;
    }
}
