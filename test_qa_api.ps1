# QA Test Script: End-to-End Architecture Analyzer
# Requisitos: Serviços rodando localmente (Gateway na porta 8080)

param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [int]$MaxPollingAttempts = 20,
    [int]$PollingIntervalSeconds = 3
)

$ErrorActionPreference = "Stop"
$authHeaderValue = if ($env:QA_AUTH_HEADER) { $env:QA_AUTH_HEADER } else { "Basic YWRtaW46cGFzc3dvcmQ=" }

$testFile = Join-Path $PSScriptRoot "diagrama_teste.png"
$spoofFile = Join-Path $PSScriptRoot "evil.pdf"
$largeFile = Join-Path $PSScriptRoot "large.png"

function Remove-TestFiles {
    Remove-Item $testFile, $spoofFile, $largeFile -ErrorAction SilentlyContinue
}

function Invoke-JsonCurl {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string[]]$ExtraArgs
    )

    $curlArgs = @("-sS", "-X") + $ExtraArgs
    $result = & curl.exe @curlArgs $Url
    if ([string]::IsNullOrWhiteSpace($result)) {
        return $null
    }
    return ($result | ConvertFrom-Json)
}

try {
    Remove-TestFiles

    # Criar PNG 1x1 se não existir
    if (-not (Test-Path $testFile)) {
        $base64Png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
        $bytes = [System.Convert]::FromBase64String($base64Png)
        [System.IO.File]::WriteAllBytes($testFile, $bytes)
    }

    Write-Host "`n--- [QA TEST] 1. Upload de diagrama ---" -ForegroundColor Cyan
    $uploadResponse = Invoke-JsonCurl -Url "$BaseUrl/upload" -ExtraArgs @("POST", "-H", "Authorization: $authHeaderValue", "-F", "file=@$testFile")

    if (-not $uploadResponse -or -not $uploadResponse.diagramId) {
        throw "Falha no upload: resposta inválida ou sem diagramId."
    }

    $diagramId = $uploadResponse.diagramId
    $uploadStatus = $uploadResponse.status
    Write-Host "[SUCCESS] Upload realizado. ID: $diagramId | status: $uploadStatus" -ForegroundColor Green

    if ($uploadStatus -ne "Recebido") {
        throw "Status do upload fora do contrato PT-BR. Esperado 'Recebido', atual '$uploadStatus'."
    }

    Write-Host "`n--- [QA TEST] 2. Polling de status (PT-BR) ---" -ForegroundColor Cyan
    $finalStatus = $null
    for ($i = 1; $i -le $MaxPollingAttempts; $i++) {
        $statusResponse = Invoke-JsonCurl -Url "$BaseUrl/status/$diagramId" -ExtraArgs @("GET", "-H", "Authorization: $authHeaderValue")
        if (-not $statusResponse) {
            Write-Host "[WARN] Resposta vazia em status (tentativa $i/$MaxPollingAttempts)" -ForegroundColor Yellow
            Start-Sleep -Seconds $PollingIntervalSeconds
            continue
        }

        $state = $statusResponse.state
        Write-Host "[INFO] Tentativa $i/$MaxPollingAttempts - Status atual: $state"

        if ($state -eq "Analisado" -or $state -eq "Erro") {
            $finalStatus = $state
            break
        }

        Start-Sleep -Seconds $PollingIntervalSeconds
    }

    if (-not $finalStatus) {
        throw "Timeout de polling sem status final (Analisado/Erro)."
    }

    Write-Host "[INFO] Status final: $finalStatus" -ForegroundColor Yellow

    if ($finalStatus -eq "Analisado") {
        Write-Host "`n--- [QA TEST] 3. Buscar relatório final ---" -ForegroundColor Cyan
        $reportRaw = & curl.exe -sS -X GET -H "Authorization: $authHeaderValue" "$BaseUrl/reports/$diagramId"
        if ([string]::IsNullOrWhiteSpace($reportRaw)) {
            throw "Relatório vazio para status Analisado."
        }

        $report = $reportRaw | ConvertFrom-Json
        if (-not $report.content) {
            throw "Relatório sem campo content."
        }
        Write-Host "[SUCCESS] Relatório recuperado com sucesso." -ForegroundColor Green
    }

    Write-Host "`n--- [QA TEST] 4. Edge Case: MIME Spoofing ---" -ForegroundColor Cyan
    "not-a-pdf" | Out-File -FilePath $spoofFile -Encoding ascii
    $spoofResponse = Invoke-JsonCurl -Url "$BaseUrl/upload" -ExtraArgs @("POST", "-H", "Authorization: $authHeaderValue", "-F", "file=@$spoofFile")
    if ($spoofResponse -and $spoofResponse.status -ne "BAD_REQUEST") {
        throw "MIME spoofing deveria retornar BAD_REQUEST. Resposta: $($spoofResponse | ConvertTo-Json -Compress)"
    }
    Write-Host "[SUCCESS] MIME spoofing bloqueado." -ForegroundColor Green

    Write-Host "`n--- [QA TEST] 5. Edge Case: Arquivo gigante ---" -ForegroundColor Cyan
    $file = [System.IO.File]::Create($largeFile)
    $file.SetLength(11MB)
    $file.Close()

    $largeResponse = Invoke-JsonCurl -Url "$BaseUrl/upload" -ExtraArgs @("POST", "-H", "Authorization: $authHeaderValue", "-F", "file=@$largeFile")
    if ($largeResponse -and $largeResponse.status -ne "BAD_REQUEST") {
        throw "Arquivo gigante deveria retornar BAD_REQUEST. Resposta: $($largeResponse | ConvertTo-Json -Compress)"
    }
    Write-Host "[SUCCESS] Arquivo acima do limite bloqueado." -ForegroundColor Green

    Write-Host "`n[QA] Testes E2E concluídos com sucesso." -ForegroundColor Green
    exit 0
}
catch {
    Write-Host "`n[QA][FALHA] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
finally {
    Remove-TestFiles
}
