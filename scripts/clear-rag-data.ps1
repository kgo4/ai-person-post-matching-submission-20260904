param(
    [string]$MySqlHost = "localhost",
    [int]$MySqlPort = 3306,
    [string]$Database = "hrms_db",
    [string]$User = "root",
    [string]$Password = $env:MYSQL_PASSWORD,
    [switch]$SkipMySql,
    [switch]$DropMilvusCollection
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $root "sql/clear-rag-knowledge-and-vectors.sql"

if (-not $SkipMySql) {
    if ([string]::IsNullOrWhiteSpace($Password)) {
        throw "请通过 -Password 或 MYSQL_PASSWORD 提供数据库密码"
    }
    $mysql = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysql) {
        throw "未找到 mysql 客户端；可先执行 mysql --version，或使用 docker exec matching-mysql mysql"
    }
    & $mysql.Source --host=$MySqlHost --port=$MySqlPort --user=$User --password=$Password $Database < $sqlFile
    if ($LASTEXITCODE -ne 0) { throw "RAG MySQL 清库失败，退出码 $LASTEXITCODE" }
}

if ($DropMilvusCollection) {
    Write-Warning "请在 Milvus 管理端删除 rag_knowledge_chunks；脚本不会误删 person_post_vector。"
}

Write-Output "RAG MySQL 清库完成。业务主数据未处理。"
