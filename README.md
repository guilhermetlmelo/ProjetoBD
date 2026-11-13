# ProjetoBD: Banco de Dados de um Bar

Trabalho acadêmico da disciplina de **Modelagem de Banco de Dados** (CESAR School).

O minimundo é um **bar**: pessoas (clientes e funcionários) com seus telefones, mesas,
produtos do cardápio, atendimentos, os pedidos feitos em cada atendimento e os
pagamentos parciais da conta. O banco é implementado em **PostgreSQL 16** e a interface
é uma aplicação web em **Java 17 + Spring Boot 3** com acesso por JDBC.

## Sumário

- [Estrutura do repositório](#estrutura-do-repositório)
- [Responsáveis](#responsáveis)
- [Requisitos](#requisitos)
- [Setup no Windows](#setup-no-windows)
- [Como rodar o banco](#como-rodar-o-banco)
- [Aplicação (Etapas 03 a 06)](#aplicação-etapas-03-a-06)
- [Etapa 06: versão final](#etapa-06-versão-final)
- [Testes automatizados](#testes-automatizados)
- [Fluxo de trabalho com Git](#fluxo-de-trabalho-com-git)

## Estrutura do repositório

```
ProjetoBD/
├── README.md
├── ENTREGA_LINK.txt              # link do repositório para a entrega
├── Guia_Apresentacao_Java.pdf    # guia de estudo para a apresentação (versão final)
├── sql/                          # executados em ordem alfabética pelo rodar_tudo.ps1
│   ├── 00_drop_all.sql
│   ├── 01_pessoa.sql ... 09_pagamento_parcial.sql   # Etapa 02: tabelas e dados
│   ├── 10_indices.sql            # Etapa 04: índices nas FKs usadas pelas consultas/visões
│   ├── 11_visoes.sql             # Etapa 04: vw_itens_vendidos e vw_historico_cliente
│   ├── 12_funcoes.sql            # Etapa 05: fn_valor_conta e fn_situacao_conta
│   ├── 13_procedimentos.sql      # Etapa 05: reajuste de preços e redistribuição de supervisores (cursor)
│   ├── 14_triggers.sql           # Etapa 05: log de preços e bloqueio de pedido em conta fechada
│   ├── 15_carga_historico.sql    # Etapa 06: movimento de julho a setembro para o dashboard
│   └── consultas/
│       ├── consultas.sql         # Etapa 03: SQL das consultas
│       └── etapa04_consultas.sql # Etapa 04: anti join, full outer join e subconsultas
├── dados/
│   └── referencia.md             # dados de referência usados nos INSERTs
├── scripts/
│   └── rodar_tudo.ps1            # recria o banco e executa todos os scripts
└── app/                          # aplicação Java (Spring Boot + JDBC)
    ├── src/main/java/br/cesar/bar/
    │   ├── controller/           # rotas das telas
    │   ├── repository/           # todo o SQL da aplicação
    │   ├── model/                # records com os dados das telas
    │   └── estatistica/          # p-valor do teste t (correlação de Pearson)
    ├── src/main/resources/       # templates Thymeleaf, CSS e JavaScript dos gráficos
    └── src/test/java/            # testes automatizados
```

O **prefixo numérico** dos arquivos define a ordem de execução, que é a ordem de
dependência das chaves estrangeiras: uma tabela só pode ser criada depois das tabelas
que ela referencia.

## Responsáveis

| Arquivo                      | Tabela            | Responsável | Depende de                                     |
|------------------------------|-------------------|-------------|------------------------------------------------|
| `00_drop_all.sql`            | (todas)           | Gui         | -                                              |
| `01_pessoa.sql`              | PESSOA            | Gui         | -                                              |
| `02_telefone.sql`            | TELEFONE          | Gui         | PESSOA                                         |
| `03_cliente.sql`             | CLIENTE           | Rafael      | PESSOA                                         |
| `04_funcionario.sql`         | FUNCIONARIO       | Rafael      | PESSOA (e autorrelacionamento com FUNCIONARIO) |
| `05_mesa.sql`                | MESA              | João        | -                                              |
| `06_produto.sql`             | PRODUTO           | João        | -                                              |
| `07_atendimento.sql`         | ATENDIMENTO       | Lucca       | CLIENTE, FUNCIONARIO, MESA                     |
| `08_pedido.sql`              | PEDIDO            | Pedro       | ATENDIMENTO, PRODUTO                           |
| `09_pagamento_parcial.sql`   | PAGAMENTO_PARCIAL | Pedro       | ATENDIMENTO                                    |

## Requisitos

- **PostgreSQL 16** (servidor + `psql`)
- **JDK 17 ou superior** (o Java 8 não serve). Ex.: [Eclipse Temurin 21](https://adoptium.net/)
- **Git**
- **VS Code** ou IntelliJ
- Windows 10/11 com PowerShell

Não é preciso instalar o Maven: o projeto usa o Maven Wrapper (`mvnw.cmd`).

## Setup no Windows

### 1. PostgreSQL 16

1. Baixe o instalador em <https://www.postgresql.org/download/windows/> (instalador da EDB).
2. Na instalação, mantenha os padrões e defina:
   - **Senha do usuário `postgres`:** `bd2026`
   - **Porta:** `5432`
3. Todos do grupo devem usar essa mesma senha, porque o `rodar_tudo.ps1` a utiliza por padrão.

### 2. Colocar o `psql` no PATH

O instalador **não** adiciona o `psql` ao PATH. Sem isso, o comando `psql` não é reconhecido.

1. Aperte `Win`, digite **"variáveis de ambiente"** e abra **"Editar as variáveis de ambiente do sistema"**.
2. Clique em **Variáveis de Ambiente...**
3. Em **Variáveis de usuário**, selecione `Path`, clique em **Editar**, depois em **Novo** e adicione:
   ```
   C:\Program Files\PostgreSQL\16\bin
   ```
4. Clique em OK em todas as janelas e **feche e abra de novo** o terminal (e o VS Code).
5. Confira:
   ```powershell
   psql --version
   # psql (PostgreSQL) 16.x
   ```

> Para testar sem mexer nas configurações, dá para adicionar só na sessão atual:
> `$env:Path += ";C:\Program Files\PostgreSQL\16\bin"`

### 3. Git

1. Instale o Git: <https://git-scm.com/download/win>
2. Configure seu nome e e-mail (use o mesmo e-mail da sua conta do GitHub):
   ```powershell
   git config --global user.name "Seu Nome"
   git config --global user.email "seu@email.com"
   ```
3. Clone o repositório:
   ```powershell
   cd ~\Documents
   git clone https://github.com/guilhermetlmelo/ProjetoBD.git
   cd ProjetoBD
   ```

### 4. VS Code

1. Instale o VS Code: <https://code.visualstudio.com/>
2. Abra a pasta do projeto: `code .`
3. (Opcional) Instale uma extensão de PostgreSQL (ex.: **PostgreSQL**, da Microsoft) para
   consultar o banco pelo editor: host `localhost`, porta `5432`, usuário `postgres`,
   senha `bd2026`, banco `bar`.

> **Encoding:** salve os arquivos `.sql` em **UTF-8 sem BOM** (o padrão do VS Code).
> O BOM faz o `psql` dar erro de sintaxe na primeira linha do arquivo.

### 5. Permitir a execução de scripts PowerShell

Por padrão o Windows bloqueia scripts `.ps1`. Rode uma vez:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

## Como rodar o banco

Na raiz do repositório:

```powershell
.\scripts\rodar_tudo.ps1
```

O script:

1. Recria o banco do zero (`DROP DATABASE IF EXISTS bar` + `CREATE DATABASE bar`);
2. Executa todos os arquivos de `sql/` em ordem alfabética;
3. Para no primeiro erro e mostra qual arquivo falhou;
4. Ao final, mostra a contagem de linhas de cada tabela.

Cada arquivo roda em uma única transação: se der erro no meio, nada daquele arquivo fica no banco.

Se a sua instalação usa outra porta ou outra senha, passe por parâmetro:

```powershell
.\scripts\rodar_tudo.ps1 -Porta 5433 -Senha minhasenha
```

> **Atenção:** o script **apaga o banco `bar`** toda vez que roda, derrubando conexões abertas
> nele (inclusive a do VS Code). Não guarde nada manualmente nesse banco: tudo precisa
> estar nos arquivos de `sql/`.

Contagem esperada depois do script:

| Tabela | Linhas | | Tabela | Linhas |
|---|---:|---|---|---:|
| PESSOA | 65 | | ATENDIMENTO | 306 |
| TELEFONE | 85 | | PEDIDO | 1385 |
| CLIENTE | 30 | | PAGAMENTO_PARCIAL | 587 |
| FUNCIONARIO | 35 | | LOG_PRECO_PRODUTO | 0 |
| MESA | 30 | | PRODUTO | 30 |

## Aplicação (Etapas 03 a 06)

Interface web em **Java 17 + Spring Boot 3**, acessando o PostgreSQL por **JDBC puro**
(`JdbcTemplate`). Não há JPA/Hibernate: todo comando SQL está escrito explicitamente nas
classes de `app/src/main/java/br/cesar/bar/repository/`.

### Como rodar a aplicação

```powershell
.\scripts\rodar_tudo.ps1     # (re)cria o banco, se ainda não criou
cd app
.\mvnw.cmd spring-boot:run
```

Depois abra <http://localhost:8080>. Na primeira execução o wrapper baixa o Maven e as
dependências, o que pode levar alguns minutos.

A conexão usa `localhost:5432`, usuário `postgres`, senha `bd2026`
(`app/src/main/resources/application.properties`). Para usar outros valores:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/bar"; $env:DB_PASSWORD = "minhasenha"
.\mvnw.cmd spring-boot:run
```

Para gerar um `.jar` executável: `.\mvnw.cmd package` e depois `java -jar target\bar-1.0.0.jar`.

Os gráficos usam o Chart.js 4.4.1 (licença MIT), que fica dentro do projeto em
`app/src/main/resources/static/js/vendor/`: a aplicação funciona sem internet.

### Telas

| Tela | O que faz | Onde está o SQL |
|------|-----------|-----------------|
| **Dashboard** | Indicadores, estatística descritiva e 9 gráficos com filtros de período, categoria e área | `DashboardRepository` |
| **Consultas** | As 9 consultas das Etapas 03 e 04 e as 2 visões, com filtros, indicadores e gráfico | `ConsultaRepository` |
| **Atendimentos** | CRUD de ATENDIMENTO, PEDIDO e PAGAMENTO_PARCIAL, fechamento e reabertura da conta | `AtendimentoRepository` |
| **Rotinas do banco** | Executa as funções, procedimentos e triggers da Etapa 05 e mostra o efeito | `RotinaRepository` |
| **Produtos** | CRUD de PRODUTO (a mudança de preço dispara o trigger de log) | `ProdutoRepository` |
| **Mesas** | CRUD de MESA | `MesaRepository` |
| **Clientes** | CRUD de PESSOA + CLIENTE + TELEFONE numa transação | `ClienteRepository` |
| **Funcionários** | CRUD de PESSOA + FUNCIONARIO + TELEFONE, com supervisor e filtro por cargo | `FuncionarioRepository` |
| **Tabelas** | Conteúdo das 10 tabelas e das 2 visões | `TabelaRepository` |

Erros do banco (chave duplicada, FK, CHECK, exceções levantadas por trigger ou procedimento)
aparecem na tela com a mensagem do PostgreSQL. Ex.: excluir um produto que já foi pedido é
barrado pela FK de PEDIDO.

## Etapa 06: versão final

Como cada requisito da Etapa 06 foi atendido:

### CRUD em pelo menos 4 tabelas

Inserção, alteração e exclusão pela interface em **9 tabelas**:

| Tela | Tabelas |
|---|---|
| Produtos | PRODUTO |
| Mesas | MESA |
| Clientes | PESSOA, CLIENTE, TELEFONE |
| Funcionários | PESSOA, FUNCIONARIO, TELEFONE |
| Atendimentos | ATENDIMENTO, PEDIDO, PAGAMENTO_PARCIAL |

A tabela LOG_PRECO_PRODUTO é preenchida pelo trigger e aparece em Rotinas do banco e em Tabelas.

### Integração com funções, procedimentos e triggers (Etapa 05)

| Rotina | Onde a interface usa ou mostra o efeito |
|---|---|
| `fn_valor_conta` | Valor de cada conta em Atendimentos; consulta direta em Rotinas do banco |
| `fn_situacao_conta` | Situação de cada conta, filtro de Atendimentos, regra para fechar a conta (só fecha QUITADA, PAGO A MAIS ou SEM CONSUMO), indicador "Contas quitadas" do dashboard e gráfico de contas por situação |
| `sp_reajustar_preco_categoria` | Formulário em Rotinas do banco; as mensagens `RAISE NOTICE` aparecem na tela e o gráfico de preço médio muda |
| `sp_redistribuir_sem_supervisor` (cursor) | Em Rotinas do banco: simula a saída de um gerente (equipe sem supervisor, como o `ON DELETE SET NULL`) e executa o procedimento, mostrando cada redistribuição |
| `trg_log_preco_produto` | Tabela LOG_PRECO_PRODUTO atualizada a cada reajuste ou alteração de preço na tela Produtos |
| `trg_pedido_conta_fechada` | Lançar item em conta fechada mostra o erro do trigger (tela do atendimento e teste com ROLLBACK em Rotinas do banco) |

O código de cada rotina é lido do próprio banco (`pg_get_functiondef`) e exibido na tela.

### Consultas e visões com filtros

Cada consulta e visão abre numa aba própria com filtros, indicadores (linhas, total, média,
maior valor ou moda) e um gráfico. O SQL executado aparece no fim da página. Os filtros
viram parâmetros nomeados no SQL: campo vazio vira NULL e a condição deixa de filtrar.

| Consulta / visão | Filtros | Gráfico |
|---|---|---|
| Faturamento por categoria | período, área | rosca |
| Ranking de atendentes | período, cargo | barras horizontais |
| Conferência de contas | período, status, situação | rosca (contagem) |
| Clientes acima da média | período, fidelidade | barras horizontais |
| Hierarquia de supervisão | cargo do subordinado | barras |
| Mesas ociosas (anti join) | período, área | barras (contagem) |
| Dia A x dia B (full outer join) | os dois dias, categoria | barras agrupadas |
| Clientes de todas as categorias | fidelidade, mínimo de visitas | barras horizontais |
| Produtos acima da média da categoria | categoria | barras agrupadas |
| `vw_itens_vendidos` | período, categoria, área, garçom, status | barras horizontais (soma) |
| `vw_historico_cliente` | segmento, fidelidade, sugestão | rosca (contagem) |

### Dashboard estatístico

Tudo calculado no PostgreSQL a partir dos dados do banco, a cada carregamento, com filtros de
**período**, **categoria** e **área do bar**.

**Indicadores:** faturamento (e média por noite), atendimentos, ticket médio, itens vendidos e
produto mais vendido, clientes atendidos e % com fidelidade, tendência semanal (variação da
última semana e inclinação da reta), % de contas quitadas e categoria líder.

**Estatística descritiva** do valor por atendimento: média, mediana, moda, classe modal,
variância, desvio padrão, coeficiente de variação, assimetria de Pearson, mínimo, quartis e
máximo, com tabela de frequências absoluta, relativa e acumulada.

**Gráficos (9):**

| Gráfico | Tipo | Análise |
|---|---|---|
| Evolução semanal | linha + reta de tendência | tendência temporal (troca entre faturamento, atendimentos e ticket) |
| Valor por atendimento | histograma | distribuição de frequências |
| Faturamento por categoria | rosca | participação |
| Forma de pagamento | pizza | participação |
| Ticket por dia da semana | barras agrupadas | média x mediana |
| Perfil de consumo por área | radar | comparativo entre áreas |
| 10 produtos mais vendidos | barras horizontais | ranking de frequência |
| Itens por hora do dia | barras | distribuição do horário de pico |
| Permanência x valor gasto | dispersão + regressão | correlação de Pearson, r², teste t e p-valor |

### Carga de dados da Etapa 06

Os dados das etapas anteriores cobriam só 7 noites de setembro, pouco para analisar tendência.
O `sql/15_carga_historico.sql` lança o movimento das quintas a domingos de 02/07 a 18/09:
é determinístico (`setseed`, todo mundo gera os mesmos números), respeita a data da primeira
visita de cada cliente, não usa as mesas que eram ociosas, passa pela regra do trigger (abre
a conta, lança pedidos e pagamentos e só depois fecha) e não altera nada do que já existia.

## Testes automatizados

```powershell
.\scripts\rodar_tudo.ps1     # os testes de integração usam o banco bar
cd app
.\mvnw.cmd test
```

São 121 testes (JUnit 5 + MockMvc):

- **Integração** contra o PostgreSQL real: todas as rotas das telas, os filtros, os caminhos de
  erro (chave duplicada, FK, CHECK, exceção de trigger e de procedimento) e o efeito das
  rotinas da Etapa 05. Cada teste roda numa transação desfeita no final, então o banco continua
  igual ao gerado pelo `rodar_tudo.ps1`. Sem o banco no ar, esses testes são pulados.
- **Unitários**: formatação, tradução de erros do banco, p-valor do teste t e modelos.

O relatório de cobertura (JaCoCo) fica em `app/target/site/jacoco/index.html`. Com o banco no
ar, a cobertura é de 100% das linhas e ramos.

## Fluxo de trabalho com Git

### Passo a passo

**Primeira vez** (criar a sua branch):

```powershell
git checkout main
git pull
git checkout -b gui          # use o seu nome
git push -u origin gui
```

**Todo dia, antes de começar** (trazer as novidades da `main`):

```powershell
git checkout gui
git pull origin main
```

**Depois de editar:**

```powershell
.\scripts\rodar_tudo.ps1                # tem que rodar sem erro antes de subir
git status
git add sql/01_pessoa.sql               # adicione só os seus arquivos
git commit -m "Cria tabela PESSOA"
git push
```

**Abrir o Pull Request:**

1. No GitHub, vá em **Pull requests**, depois em **New pull request**.
2. Base: `main`, compare: `gui` (a sua branch).
3. Descreva o que foi feito e peça revisão de pelo menos uma pessoa do grupo.
4. Depois de aprovado, faça o merge. Os outros atualizam as branches com `git pull origin main`.

### Dependências entre pessoas

Como as tabelas dependem umas das outras, a ordem de merge importa:

1. **Gui** (PESSOA, TELEFONE) e **João** (MESA, PRODUTO) não dependem de ninguém.
2. **Rafael** (CLIENTE, FUNCIONARIO) depende de PESSOA (Gui).
3. **Lucca** (ATENDIMENTO) depende de CLIENTE, FUNCIONARIO (Rafael) e MESA (João).
4. **Pedro** (PEDIDO, PAGAMENTO_PARCIAL) depende de ATENDIMENTO (Lucca) e PRODUTO (João).

Se a tabela da qual você depende ainda não está na `main`, o `rodar_tudo.ps1` vai falhar
no seu arquivo. Nesse caso, espere o merge ou traga a branch do colega para testar
localmente (`git pull origin rafael`, por exemplo).

> **Dica:** peça ao dono do repositório para proteger a `main` no GitHub
> (**Settings**, **Branches**, **Add branch protection rule**, **Require a pull request before merging**).
> Assim o próprio GitHub impede commits diretos.
