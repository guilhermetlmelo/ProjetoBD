# ProjetoBD — Banco de Dados de um Bar

Trabalho acadêmico da disciplina de **Modelagem de Banco de Dados** (CESAR School).

O minimundo é um **bar**: pessoas (clientes e funcionários) com seus telefones, mesas,
produtos do cardápio, atendimentos, os pedidos feitos em cada atendimento e os
pagamentos parciais da conta. O banco é implementado em **PostgreSQL 16**.

## Estrutura do repositório

```
ProjetoBD/
├── README.md
├── sql/                         # um arquivo por tabela, executados em ordem alfabética
│   ├── 00_drop_all.sql
│   ├── 01_pessoa.sql
│   ├── 02_telefone.sql
│   ├── 03_cliente.sql
│   ├── 04_funcionario.sql
│   ├── 05_mesa.sql
│   ├── 06_produto.sql
│   ├── 07_atendimento.sql
│   ├── 08_pedido.sql
│   ├── 09_pagamento_parcial.sql
│   ├── 10_indices.sql           # Etapa 04: índices nas FKs usadas pelas consultas/visões
│   ├── 11_visoes.sql            # Etapa 04: vw_itens_vendidos e vw_historico_cliente
│   ├── 12_funcoes.sql           # Etapa 05: fn_valor_conta e fn_situacao_conta
│   ├── 13_procedimentos.sql     # Etapa 05: reajuste de precos e redistribuicao de supervisores (cursor)
│   ├── 14_triggers.sql          # Etapa 05: log de precos e bloqueio de pedido em conta fechada
│   └── consultas/
│       ├── consultas.sql        # Etapa 03: SQL de todas as consultas da aplicação
│       └── etapa04_consultas.sql # Etapa 04: anti join, full outer join e subconsultas
├── dados/
│   └── referencia.md            # dados de referência usados nos INSERTs
├── scripts/
│   └── rodar_tudo.ps1           # recria o banco e executa todos os scripts
├── app/                         # Etapa 03: aplicação Java (Spring Boot + JDBC)
└── ENTREGA_LINK.txt             # link do repositório para a entrega
```

O **prefixo numérico** dos arquivos define a ordem de execução, que é a ordem de
dependência das chaves estrangeiras: uma tabela só pode ser criada depois das tabelas
que ela referencia.

## Responsáveis

| Arquivo                      | Tabela            | Responsável | Depende de                                     |
|------------------------------|-------------------|-------------|------------------------------------------------|
| `00_drop_all.sql`            | (todas)           | Gui         | —                                              |
| `01_pessoa.sql`              | PESSOA            | Gui         | —                                              |
| `02_telefone.sql`            | TELEFONE          | Gui         | PESSOA                                         |
| `03_cliente.sql`             | CLIENTE           | Rafael      | PESSOA                                         |
| `04_funcionario.sql`         | FUNCIONARIO       | Rafael      | PESSOA (e autorrelacionamento com FUNCIONARIO) |
| `05_mesa.sql`                | MESA              | João        | —                                              |
| `06_produto.sql`             | PRODUTO           | João        | —                                              |
| `07_atendimento.sql`         | ATENDIMENTO       | Lucca       | CLIENTE, FUNCIONARIO, MESA                     |
| `08_pedido.sql`              | PEDIDO            | Pedro       | ATENDIMENTO, PRODUTO                           |
| `09_pagamento_parcial.sql`   | PAGAMENTO_PARCIAL | Pedro       | ATENDIMENTO                                    |

## Requisitos

- **PostgreSQL 16** (servidor + `psql`)
- **Git**
- **VS Code**
- Windows 10/11 com PowerShell

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
3. Em **Variáveis de usuário**, selecione `Path` → **Editar** → **Novo** e adicione:
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

## Como rodar o projeto

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

> ⚠️ O script **apaga o banco `bar`** toda vez que roda, derrubando conexões abertas
> nele (inclusive a do VS Code). Não guarde nada manualmente nesse banco: tudo precisa
> estar nos arquivos de `sql/`.

## Aplicação (Etapa 03)

Interface web com dashboard, em **Java 17 + Spring Boot 3**, acessando o PostgreSQL por
**JDBC puro** (`JdbcTemplate`). Não há JPA/Hibernate: todo comando SQL está escrito
explicitamente nas classes de `app/src/main/java/br/cesar/bar/repository/`.

| Tela | O que faz | Onde está o SQL |
|------|-----------|-----------------|
| **Dashboard** | Indicadores, estatísticas descritivas (média, mediana, desvio padrão, quartis), histograma com tabela de frequências, correlação de Pearson entre tempo de permanência e valor gasto (dispersão, reta de regressão e teste t) e gráficos (faturamento por dia, categoria, hora, produto, forma de pagamento e área) | `DashboardRepository` |
| **Consultas** | Executa as 5 consultas da entrega e mostra o resultado + o SQL | `ConsultaRepository` |
| **Produtos** | Inserção, alteração e exclusão em PRODUTO | `ProdutoRepository` |
| **Mesas** | Inserção, alteração e exclusão em MESA | `MesaRepository` |
| **Clientes** | Inserção, alteração e exclusão em PESSOA + CLIENTE + TELEFONE (numa transação) | `ClienteRepository` |
| **Tabelas** | Visualização do conteúdo das 9 tabelas | `TabelaRepository` |

Consultas (todas também em `sql/consultas/consultas.sql`):

1. **Faturamento por categoria** — JOIN, GROUP BY, função de janela `SUM() OVER ()` para o percentual.
2. **Ranking de atendentes** — JOIN de 6 tabelas, LEFT JOIN do supervisor, `RANK() OVER`.
3. **Conferência de contas (consumido x pago)** — duas CTEs agregadas, LEFT JOIN, CASE.
4. **Clientes acima da média de gasto** — CTE, subconsulta com AVG no WHERE, subconsulta correlacionada (produto favorito).
5. **Hierarquia de supervisão** — autojunção em FUNCIONARIO, STRING_AGG, SUM/AVG.

Erros do banco (chave duplicada, FK, CHECK) aparecem na tela com a mensagem do PostgreSQL:
por exemplo, excluir um produto que já foi pedido é barrado pela FK de PEDIDO.

### Requisitos da aplicação

- **JDK 17 ou superior** (o Java 8 não serve). Ex.: [Eclipse Temurin 21](https://adoptium.net/).
- Não é preciso instalar o Maven: o projeto usa o Maven Wrapper (`mvnw.cmd`).
- O banco `bar` criado pelo `rodar_tudo.ps1` (veja acima).

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

1. No GitHub, vá em **Pull requests → New pull request**.
2. Base: `main` ← compare: `gui` (a sua branch).
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
> (**Settings → Branches → Add branch protection rule → Require a pull request before merging**).
> Assim o próprio GitHub impede commits diretos.
