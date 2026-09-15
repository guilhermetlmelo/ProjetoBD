<#
.SYNOPSIS
    Recria o banco "bar" do zero e executa todos os scripts de sql/ em ordem.

.DESCRIPTION
    1. DROP DATABASE IF EXISTS bar; CREATE DATABASE bar;
    2. Executa cada arquivo sql/*.sql em ordem alfabética (00_, 01_, 02_...)
    3. Para no primeiro erro e informa qual arquivo falhou
    4. Mostra a contagem de linhas de cada tabela

.EXAMPLE
    .\scripts\rodar_tudo.ps1

.EXAMPLE
    .\scripts\rodar_tudo.ps1 -Porta 5433
#>
param(
    [string]$Servidor = 'localhost',
    [int]   $Porta    = 5432,
    [string]$Usuario  = 'postgres',
    [string]$Senha    = 'bd2026',
    [string]$Banco    = 'bar'
)

$ErrorActionPreference = 'Stop'

function Escrever-Titulo([string]$texto) {
    Write-Host ''
    Write-Host "=== $texto ===" -ForegroundColor Cyan
}

function Encerrar-ComErro([string]$mensagem) {
    Write-Host ''
    Write-Host "ERRO: $mensagem" -ForegroundColor Red
    Restaurar-Ambiente
    exit 1
}

# --- Localiza o psql -----------------------------------------------------------
$psql = Get-Command psql -ErrorAction SilentlyContinue
if ($psql) {
    $psql = $psql.Source
} elseif (Test-Path 'C:\Program Files\PostgreSQL\16\bin\psql.exe') {
    $psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
    Write-Host 'Aviso: psql não está no PATH; usando C:\Program Files\PostgreSQL\16\bin (veja o README).' -ForegroundColor Yellow
} else {
    Write-Host 'ERRO: psql não encontrado. Instale o PostgreSQL 16 e adicione' -ForegroundColor Red
    Write-Host '      C:\Program Files\PostgreSQL\16\bin ao PATH (veja o README).' -ForegroundColor Red
    exit 1
}

# --- Ambiente do psql ------------------------------------------------------------
# PGPASSWORD evita o prompt de senha; PGCLIENTENCODING garante que acentos
# nos arquivos .sql (salvos em UTF-8) cheguem corretos ao servidor.
$antigoPgPassword = $env:PGPASSWORD
$antigoPgEncoding = $env:PGCLIENTENCODING
$env:PGPASSWORD       = $Senha
$env:PGCLIENTENCODING = 'UTF8'

function Restaurar-Ambiente {
    $env:PGPASSWORD       = $antigoPgPassword
    $env:PGCLIENTENCODING = $antigoPgEncoding
}

# Argumentos comuns: -X ignora o psqlrc do usuário, ON_ERROR_STOP faz o psql
# sair com código != 0 no primeiro erro.
$conexao = @('-X', '-q', '-h', $Servidor, '-p', $Porta, '-U', $Usuario, '-v', 'ON_ERROR_STOP=1')

$raiz     = Split-Path -Parent $PSScriptRoot
$pastaSql = Join-Path $raiz 'sql'

# --- 1. Recria o banco -----------------------------------------------------------
Escrever-Titulo "Recriando o banco '$Banco'"

# DROP/CREATE DATABASE não podem rodar dentro de transação, por isso são
# executados separadamente, conectados ao banco "postgres".
# WITH (FORCE) derruba conexões abertas (ex.: VS Code conectado ao banco).
& $psql @conexao -d postgres -c "DROP DATABASE IF EXISTS $Banco WITH (FORCE);"
if ($LASTEXITCODE -ne 0) { Encerrar-ComErro "falha ao remover o banco '$Banco'. O PostgreSQL está rodando? A senha está correta?" }

& $psql @conexao -d postgres -c "CREATE DATABASE $Banco;"
if ($LASTEXITCODE -ne 0) { Encerrar-ComErro "falha ao criar o banco '$Banco'." }

Write-Host "Banco '$Banco' criado." -ForegroundColor Green

# --- 2 e 3. Executa os scripts em ordem --------------------------------------------
Escrever-Titulo 'Executando scripts de sql/'

$scripts = Get-ChildItem -Path $pastaSql -Filter '*.sql' -File | Sort-Object Name
if (-not $scripts) { Encerrar-ComErro "nenhum arquivo .sql encontrado em $pastaSql" }

foreach ($arquivo in $scripts) {
    Write-Host "-> $($arquivo.Name)"
    # -1 executa o arquivo inteiro em uma única transação: se algo falhar,
    # nada daquele arquivo fica pela metade no banco.
    & $psql @conexao -d $Banco -1 -f $arquivo.FullName
    if ($LASTEXITCODE -ne 0) {
        Encerrar-ComErro "o arquivo sql\$($arquivo.Name) falhou (código $LASTEXITCODE). Execução interrompida."
    }
}

Write-Host "$($scripts.Count) arquivo(s) executado(s) com sucesso." -ForegroundColor Green

# --- 4. Contagem de linhas ----------------------------------------------------------
Escrever-Titulo 'Contagem de linhas por tabela'

$qtdTabelas = & $psql @conexao -d $Banco -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';"
if ($LASTEXITCODE -ne 0) { Encerrar-ComErro 'falha ao listar as tabelas.' }

if ([int]$qtdTabelas -eq 0) {
    Write-Host 'Nenhuma tabela no schema public ainda.' -ForegroundColor Yellow
} else {
    # query_to_xml permite fazer um count(*) exato em cada tabela numa única consulta.
    $sqlContagem = @'
SELECT table_name AS tabela,
       (xpath('/row/n/text()',
              query_to_xml(format('SELECT count(*) AS n FROM %I.%I', table_schema, table_name),
                           false, true, '')))[1]::text::bigint AS linhas
  FROM information_schema.tables
 WHERE table_schema = 'public'
   AND table_type = 'BASE TABLE'
 ORDER BY table_name;
'@
    & $psql @conexao -d $Banco -P footer=off -c $sqlContagem
    if ($LASTEXITCODE -ne 0) { Encerrar-ComErro 'falha ao contar as linhas das tabelas.' }
}

Restaurar-Ambiente
Write-Host ''
Write-Host 'Concluído.' -ForegroundColor Green
