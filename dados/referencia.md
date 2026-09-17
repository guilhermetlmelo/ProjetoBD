# Dados de referência — Projeto BD

Lista fixa de valores que todo mundo usa nos INSERTs.
Se cada um inventar os seus, as chaves estrangeiras não batem na integração.

Qualquer alteração aqui: avisa no grupo antes.

## Pessoas

CPFs fictícios. Começam com 1 para clientes e 2 para funcionários,
para facilitar identificar de onde veio na hora de debugar.

| CPF | Nome | Vira |
|---|---|---|
| 10000000001 | Ana Beatriz Ramos | Cliente |
| 10000000002 | Carlos Eduardo Lima | Cliente |
| 10000000003 | Debora Nunes Alves | Cliente |
| 10000000004 | Eduardo Farias Melo | Cliente |
| 10000000005 | Fernanda Souza Rocha | Cliente |
| 10000000006 | Gabriel Torres Pinto | Cliente |
| 20000000001 | Helena Martins Sa | Funcionário — Gerente |
| 20000000002 | Igor Cavalcanti Lins | Funcionário — Bartender |
| 20000000003 | Juliana Barros Neves | Funcionário — Garçonete |
| 20000000004 | Lucas Andrade Vieira | Funcionário — Garçom |

Nomes sem acento, para evitar problema de encoding entre as máquinas.

Supervisão: a Helena (20000000001) supervisiona os outros três.
O CPF_Supervisor dela fica NULL.

## Mesas

| Número | Capacidade | Localização |
|---|---|---|
| 1 a 4 | 4 | Salão interno |
| 5 e 6 | 6 | Área externa |
| 7 e 8 | 4 | Mezanino |
| 9 e 10 | 2 | Balcão |

## Produtos

Códigos de 1 a 30.

Categorias: Cerveja, Chopp, Destilado, Drink, Sem Alcool, Petisco.

## Convenções de SQL

- Nome de tabela em MAIÚSCULA, coluna como está no modelo lógico
- Toda constraint nomeada: pk_tabela, fk_tabela_referenciada, uq_tabela_coluna
- Mínimo de 10 linhas por tabela (PEDIDO pelo menos 20)
- Sem acento nos dados
- Data e hora no formato ISO: '2026-09-15 20:30:00'