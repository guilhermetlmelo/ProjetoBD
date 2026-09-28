package br.cesar.bar.model;

import java.time.LocalDate;

/** Cliente = linha de PESSOA + linha de CLIENTE (mesmo CPF), com os telefones separados por vírgula. */
public record Cliente(String cpf, String nome, String rua, String numero, String bairro, String cep,
                      LocalDate dataPrimeiraVisita, boolean fidelidadeAtiva, String telefones) {
}
