package br.cesar.bar.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Linha de ATENDIMENTO com os nomes e os valores calculados por fn_valor_conta e fn_situacao_conta. */
public record Atendimento(int id, LocalDateTime abertura, String status, String cpfCliente, String cliente,
                          String cpfGarcom, String garcom, int mesa, String localizacao,
                          BigDecimal consumido, BigDecimal pago, String situacao) {

    public BigDecimal saldo() {
        return consumido.subtract(pago);
    }
}
