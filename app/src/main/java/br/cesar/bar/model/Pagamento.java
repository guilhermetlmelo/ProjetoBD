package br.cesar.bar.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Pagamento(int numTransacao, BigDecimal valor, String metodo, LocalDateTime horario) {
}
