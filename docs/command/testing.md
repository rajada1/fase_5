# Guia de Configuração e Testes

## 1. Pré-requisitos
Execute `docker-compose up -d` na pasta raiz para iniciar:
- **LocalStack** (Mock da AWS para S3, SQS, SNS)
- **PostgreSQL** (com os bancos de dados criados via script de inicialização)

Aguarde a inicialização.

## 2. Iniciar os Microsserviços nativamente (ou compilar imagens Docker)
Inicie todos os microsserviços interativamente na sua IDE ou utilizando `mvn spring-boot:run` / `uvicorn main:app --reload`.
- API Gateway (8080)
- Upload Service (8081)
- Processing Service (8082)
- AI Analysis Service (8083)
- Report Service (8084)
- Status Service (8085)

## 3. Fazer Upload de um Arquivo
```bash
curl -X POST http://localhost:8080/api/v1/upload \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ=" \
  -F "file=@/caminho/para/diagrama.png"
```
**Saída Esperada**: JSON com um `diagramId` e `"status": "Recebido"`.

## 4. Verificar o Status
Use o `diagramId` recebido na etapa de upload.
```bash
curl -X GET http://localhost:8080/api/v1/status/{diagramId} \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```
**Saída Esperada**: O estado avança para `Em processamento` e finaliza em `Analisado` (ou `Erro` em caso de falha).

## 5. Buscar o Relatório Final
Assim que o status estiver `Analisado`, busque o relatório de arquitetura gerado pela IA:
```bash
curl -X GET http://localhost:8080/api/v1/reports/{diagramId} \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

## 6. Execução automatizada E2E (PowerShell)

Você pode executar o teste E2E local com:

```powershell
./test_qa_api.ps1
```

Parâmetros disponíveis:

- `-BaseUrl` (default: `http://localhost:8080/api/v1`)
- `-MaxPollingAttempts` (default: `20`)
- `-PollingIntervalSeconds` (default: `3`)

Para autenticação customizada, defina a variável de ambiente antes da execução:

```powershell
$env:QA_AUTH_HEADER = "Bearer <token>"
./test_qa_api.ps1
```

## 7. Execução no GitHub Actions

Workflow disponível:

- `.github/workflows/qa-e2e-manual.yml`

Modos de execução:

- **Manual** (`workflow_dispatch`) com parâmetros (`base_url`, polling e header de autenticação).
- **Contínuo por branch** (`push`) em `homolog` e `release/**` quando há alteração nos serviços do pipeline.
- **Contínuo agendado** (`schedule`) semanal para verificação de sanidade.

No modo contínuo, o workflow usa defaults estáveis:

- `BaseUrl`: `http://localhost:8080/api/v1`
- `MaxPollingAttempts`: `20`
- `PollingIntervalSeconds`: `3`

Para autenticação customizada e URLs diferentes, utilize o modo manual (`workflow_dispatch`).

Em qualquer modo, o workflow publica artifact com os logs do E2E:

- `qa-e2e-logs-<run_id>` (arquivos `qa-e2e-manual.log` ou `qa-e2e-continuo.log`)
