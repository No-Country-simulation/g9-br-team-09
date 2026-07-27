[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080/api/v1"
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    throw "Verificação Oracle ADB falhou: $Message"
}

$payload = @{
    consumo_kwh = 420
    uso_horario_pico = $true
    quantidade_equipamentos = 10
    tipo_imovel = "CASA"
    horas_alto_consumo = 8
} | ConvertTo-Json -Compress

try {
    $created = Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/analise-energetica" `
        -ContentType "application/json" `
        -Body $payload `
        -TimeoutSec 30
} catch {
    Fail "POST /analise-energetica retornou erro HTTP: $($_.Exception.Message)"
}

if ($null -eq $created.id -or "$($created.id)" -notmatch '^\d+$') {
    Fail "POST /analise-energetica não retornou um ID numérico válido."
}

$createdId = [long]$created.id

try {
    $read = Invoke-RestMethod `
        -Method Get `
        -Uri "$BaseUrl/analise-energetica/$createdId" `
        -TimeoutSec 30
} catch {
    Fail "GET /analise-energetica/$createdId retornou erro HTTP: $($_.Exception.Message)"
}

$expected = @{
    id = $createdId
    consumo_kwh = 420
    uso_horario_pico = $true
    quantidade_equipamentos = 10
    tipo_imovel = "CASA"
    horas_alto_consumo = 8
}

foreach ($field in $expected.Keys) {
    $actual = $read.PSObject.Properties[$field].Value

    if ($null -eq $actual -or $actual -ne $expected[$field]) {
        Fail "GET retornou payload inválido para '$field'."
    }
}

Write-Output "Persistência verificada pela API. ID criado: $createdId"
