# QA Test Script: End-to-End Architecture Analyzer
# Requisitos: Serviços rodando localmente (Gateway na porta 8080)

$BASE_URL = "http://localhost:8080/api/v1"
$AUTH_HEADER = @{ "Authorization" = "Basic YWRtaW46cGFzc3dvcmQ=" } # Ajuste para JWT se necessário
$TEST_FILE = "diagrama_teste.png"

# Criar arquivo de teste se não existir
if (-not (Test-Path $TEST_FILE)) {
    # 1x1 transparent PNG base64
    $base64Png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
    $bytes = [System.Convert]::FromBase64String($base64Png)
    [System.IO.File]::WriteAllBytes((Join-Path $PSScriptRoot $TEST_FILE), $bytes)
}

Write-Host "`n--- [QA TEST] 1. Testando Upload Normal ---" -ForegroundColor Cyan
$uploadResponse = curl.exe -s -X POST "$BASE_URL/upload" `
    -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ=" `
    -F "file=@$TEST_FILE" | ConvertFrom-Json

if ($uploadResponse.diagramId) {
    $id = $uploadResponse.diagramId
    Write-Host "[SUCCESS] Upload realizado. ID: $id" -ForegroundColor Green
}
else {
    Write-Host "[ERROR] Falha no upload: $($uploadResponse.message)" -ForegroundColor Red
    exit
}

Write-Host "`n--- [QA TEST] 2. Polling de Status (Aguardando ANALISADO) ---" -ForegroundColor Cyan
for ($i = 0; $i -lt 10; $i++) {
    $statusResponse = curl.exe -s -X GET "$BASE_URL/status/$id" -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ=" | ConvertFrom-Json
    $status = $statusResponse.status
    Write-Host "[INFO] Status atual: $status"
    
    if ($status -eq "Analisado") { break }
    if ($status -eq "Erro") { Write-Host "[ERROR] Falha no processamento!"; break }
    
    Start-Sleep -Seconds 3
}

Write-Host "`n--- [QA TEST] 3. Buscando Relatório Final ---" -ForegroundColor Cyan
$reportResponse = curl.exe -s -X GET "$BASE_URL/reports/$id" -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
Write-Host $reportResponse -ForegroundColor Yellow

Write-Host "`n--- [QA TEST] 4. Testando Edge Case: MIME Spoofing ---" -ForegroundColor Cyan
"not-a-pdf" | Out-File -FilePath "evil.pdf"
$spoofResponse = curl.exe -s -X POST "$BASE_URL/upload" `
    -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ=" `
    -F "file=@evil.pdf" | ConvertFrom-Json
Write-Host "Status esperado: 400. Resultado: $($spoofResponse.message)" -ForegroundColor Yellow

Write-Host "`n--- [QA TEST] 5. Testando Edge Case: Arquivo Gigante ---" -ForegroundColor Cyan
# Cria arquivo dummy de 11MB
$f = [System.IO.File]::Create("large.png")
$f.SetLength(11MB)
$f.Close()
$largeResponse = curl.exe -s -X POST "$BASE_URL/upload" `
    -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ=" `
    -F "file=@large.png" | ConvertFrom-Json
Write-Host "Status esperado: 400. Resultado: $($largeResponse.message)" -ForegroundColor Yellow

# Cleanup
Remove-Item $TEST_FILE, "evil.pdf", "large.png" -ErrorAction SilentlyContinue
Write-Host "`n[QA] Testes concluídos." -ForegroundColor Green
Remove-Item $TEST_FILE, "evil.pdf", "large.png" -ErrorAction SilentlyContinue
Write-Host "`n[QA] Testes concluídos." -ForegroundColor Green
