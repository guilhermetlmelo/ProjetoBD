package br.cesar.bar.model;

import java.math.BigDecimal;

/** Funcionário = linha de PESSOA + linha de FUNCIONARIO (mesmo CPF), com os telefones separados por vírgula. */
public record Funcionario(String cpf, String nome, String rua, String numero, String bairro, String cep,
                          String matricula, String cargo, BigDecimal salario, String cpfSupervisor,
                          String nomeSupervisor, String telefones, int atendimentos) {
}
