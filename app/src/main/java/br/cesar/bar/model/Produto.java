package br.cesar.bar.model;

import java.math.BigDecimal;

public record Produto(Integer codigoProd, String nomeProduto, BigDecimal precoAtual, String categoria) {
}
