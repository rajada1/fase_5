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
**Saída Esperada**: JSON com um `diagramId` e `"status": "RECEIVED"`.

## 4. Verificar o Status
Use o `diagramId` recebido na etapa de upload.
```bash
curl -X GET http://localhost:8080/api/v1/status/{diagramId} \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```
**Saída Esperada**: O estado avança para `PROCESSING` e finaliza em `ANALYZED` (ou `ERROR` em caso de falha).

## 5. Buscar o Relatório Final
Assim que o status estiver `ANALYZED`, busque o relatório de arquitetura gerado pela IA:
```bash
curl -X GET http://localhost:8080/api/v1/reports/{diagramId} \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```
