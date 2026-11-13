package br.cesar.bar.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Linha de PEDIDO com o produto e o valor do item (Quantidade x Preco_Atual). */
public record ItemPedido(int idPedido, int codigoProd, String produto, String categoria, int quantidade,
                         BigDecimal precoUnitario, BigDecimal valor, LocalDateTime dataHora) {
}
