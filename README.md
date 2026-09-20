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
│   └── 09_pagamento_parcial.sql
├── dados/
│   └── referencia.md            # dados de referência usados nos INSERTs
└── scripts/
    └── rodar_tudo.ps1           # recria o banco e executa todos os scripts
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
