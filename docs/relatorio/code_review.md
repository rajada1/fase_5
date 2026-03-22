# Code Review Técnico — Projeto de Análise de Diagramas Arquiteturais

Data: 2026-03-22

## 1) Resumo Executivo

Com base nos requisitos de `docs/command/analise.md`, o projeto apresenta **aderência alta** no núcleo funcional e de arquitetura (microsserviços, fluxo assíncrono, IA integrada, Docker/CI/CD).

Status geral: **Aprovado com ressalvas (pós-ajustes)**.

Riscos mais relevantes para fechamento completo dos requisitos:
- Requisito estrito de “banco próprio por serviço” parcialmente atendido.
- Segurança de comunicação leste-oeste entre serviços ainda pode evoluir para ambiente produtivo hardenizado.

---

## 2) Matriz de Conformidade por Requisito

### 2.1 Requisitos Funcionais

| Requisito | Status | Evidência | Gaps / Observações |
|---|---|---|---|
| Upload de diagrama (imagem/PDF) | **Atende** | `upload-service` valida MIME (`image/jpeg`, `image/png`, `application/pdf`), tamanho, nome, path traversal e assinatura binária (magic bytes) em `UploadController` | Sem gap crítico.
| Criação de processo de análise | **Atende** | `UploadUseCase` persiste metadados e enfileira evento via outbox (`OutboxServiceAdapter` + `OutboxEventPublisher`) | Sem gap crítico.
| Consulta de status (Recebido/Em processamento/Analisado/Erro) | **Atende** | Endpoint `GET /api/v1/status/{diagramId}` e atualização por eventos no `status-service` em PT-BR | Contrato PT-BR consolidado em código, testes e documentação.
| Geração de relatório com componentes/riscos/recomendações | **Atende** | `ai-analysis-service` retorna `AnalysisResult` e `report-service` persiste/expoe relatório (`GenerateReportUseCase`, `ReportController`) | Sem gap crítico no fluxo principal.

### 2.2 Requisitos Técnicos

| Requisito | Status | Evidência | Gaps / Observações |
|---|---|---|---|
| Arquitetura de microsserviços | **Atende** | Serviços independentes: gateway, upload, processing, ai-analysis, report, status | Sem gap crítico.
| Comunicação REST | **Atende** | Endpoints em upload/status/report e roteamento no gateway | Sem gap crítico.
| Fluxo assíncrono (fila/mensageria) | **Atende** | SNS/SQS em upload→processing→ai-analysis→report/status; pollers dedicados | Sem gap crítico.
| Clean/Hexagonal | **Parcial** | Serviços Java seguem `application/domain/infrastructure/interfaces`; Python organizado por módulos | Python não explicita fronteiras hexagonais com o mesmo rigor.
| Responsabilidade clara por serviço | **Atende** | Upload, OCR, IA, relatório e status separados por bounded context | Sem gap crítico.
| Banco de dados próprio por serviço | **Atende com ressalva documentada** | `init-dbs.sql` cria `upload_db`, `report_db`, `status_db`; exceção para serviços stateless formalizada em ADR | Decisão registrada em `docs/adr/ADR-001-servicos-stateless-e-banco-proprio.md`.
| Testes automatizados por serviço | **Atende** | Testes unitários Java/Python e E2E validados na rodada (`51 passed / 0 failed` + execução `test_qa_api.ps1` com fluxo completo e edge cases), com workflow contínuo (`push` homolog/release + `schedule`) e manual, incluindo publicação de artifact de logs | Sem gap crítico.

### 2.3 IA no Fluxo (controle, segurança e avaliação)

| Requisito | Status | Evidência | Gaps / Observações |
|---|---|---|---|
| IA parte do fluxo (não script isolado) | **Atende** | IA acionada por evento em `ai-analysis-service/app/services/sqs_poller.py` | Sem gap crítico.
| Pipeline claro de IA | **Atende** | OCR -> evento processado -> análise LLM -> evento concluído/falha -> persistência no report | Sem gap crítico.
| Controle de entrada/saída (guardrails) | **Atende** | Sanitização, truncamento e resposta JSON no `llm_service.py` | Limites e previsibilidade documentados no README.
| Tratamento de falhas da IA | **Atende** | Diferencia falha transitória e não-retryable; publica `ANALYSIS_FAILED` | Fallback mock removido, sem falso sucesso em erro de provedor.
| Persistência do resultado IA e geração do relatório | **Atende** | `report-service` consome análise e salva por `diagramId` | Sem gap crítico.
| Discussão de limitações do modelo | **Atende** | README contém seção formal de limitações do modelo de IA | Sem gap crítico.

### 2.4 Infraestrutura, DevOps, Qualidade e Observabilidade

| Requisito | Status | Evidência | Gaps / Observações |
|---|---|---|---|
| Docker | **Atende** | Dockerfiles por serviço + `docker-compose.yml` | Sem gap crítico.
| Docker Compose/K8s | **Atende** | Ambiente local com LocalStack/Postgres/Redis via Compose | Sem gap crítico.
| CI/CD com build, testes e deploy | **Atende** | `services-ci-cd.yml` e `terraform-infra.yml` (build/test/deploy + plan/apply) | Sem gap crítico.
| Logs estruturados | **Atende** | Correlação padronizada por `diagramId`, `eventType`, `queueUrl` e `correlationId` entre Java e Python (com compatibilidade legada documentada) | Sem gap crítico.
| Tratamento de erros | **Atende** | Handlers Java, descarte de payload inválido, retries/dedupe em pollers | Evoluir alerta operacional de DLQ/retry em produção.
| Testes unitários | **Atende** | Suítes Java/Python e E2E validadas na rodada (`51 passed / 0 failed`) | Sem gap crítico.
| README explicativo | **Atende** | Fluxo e operação descritos, incluindo seção formal de limitações do modelo de IA | Sem gap crítico.

### 2.5 Seção Obrigatória de Segurança

| Requisito | Status | Evidência | Gaps / Observações |
|---|---|---|---|
| Requisitos básicos de segurança adotados | **Atende** | Gateway com OAuth2/JWT, flag explícita de modo inseguro local (`ALLOW_INSECURE_LOCAL_API`), exposição mínima de actuator (`/actuator/health` e `/actuator/info`) e headers HTTP de segurança (HSTS, X-Frame-Options, X-Content-Type-Options) | Sem gap crítico.
| Validação de entradas não confiáveis | **Atende** | Validação robusta de upload (MIME, extensão, tamanho, assinatura do arquivo) e descarte de payload malformado nos pollers | Sem gap crítico.
| Uso controlado de IA (escopo/previsibilidade) | **Atende** | Prompt com formato JSON, sanitização/limites e seção de limitações no README | Sem gap crítico.
| Tratamento seguro de falhas de IA | **Atende** | Eventos de falha e atualização de status para erro, sem fallback de sucesso simulado | Sem gap crítico.
| Segurança na comunicação entre serviços | **Parcial** | Mensageria AWS + autenticação no gateway + isolamento de infra local em rede dedicada (`infra_internal`) e bindings em loopback no `docker-compose.yml` | Falta detalhar mTLS/controles de rede internos no ambiente final.
| Riscos/limitações de segurança documentados | **Atende** | Seção única consolidada no README (`Riscos e Limitações de Segurança`), cobrindo controles implementados, riscos residuais e próximos passos | Sem gap crítico.

---

## 3) Evidências Técnicas Principais (arquivos)

- `upload-service/src/main/java/com/architecture/upload/interfaces/rest/UploadController.java`
- `upload-service/src/main/java/com/architecture/upload/application/UploadUseCase.java`
- `upload-service/src/main/java/com/architecture/upload/infrastructure/outbox/OutboxServiceAdapter.java`
- `upload-service/src/main/java/com/architecture/upload/infrastructure/outbox/OutboxEventPublisher.java`
- `processing-service/app/services/sqs_poller.py`
- `ai-analysis-service/app/services/llm_service.py`
- `ai-analysis-service/app/services/sqs_poller.py`
- `report-service/src/main/java/com/architecture/report/infrastructure/messaging/SqsPollerService.java`
- `report-service/src/main/java/com/architecture/report/application/GenerateReportUseCase.java`
- `status-service/src/main/java/com/architecture/status/infrastructure/messaging/SqsPollerService.java`
- `status-service/src/main/java/com/architecture/status/application/UpdateStatusUseCase.java`
- `.github/workflows/services-ci-cd.yml`
- `.github/workflows/terraform-infra.yml`
- `docker-compose.yml`
- `init-dbs.sql`

---

## 4) Evidência de Testes (rodada atual)

Execução consolidada por ferramenta de testes do workspace:
- **51 passed / 0 failed**.

Execução E2E ponta a ponta validada:
- `test_qa_api.ps1` concluído com sucesso (upload `Recebido`, progressão para `Analisado`, relatório recuperado, bloqueio de MIME spoofing e de arquivo acima do limite).

Conclusão da rodada: contrato PT-BR, segurança de upload e suíte automatizada estão alinhados.

---

## 5) Achados Priorizados

### P1 (Média prioridade)
1. **Evoluir segurança de comunicação interna** para ambientes produtivos (segmentação de rede/controles adicionais).

### P2 (Baixa prioridade)
2. **Definir política de retenção e revisão periódica dos artifacts E2E** para auditoria operacional contínua.

---

## 6) Recomendações Objetivas (próximo sprint)

1. Evoluir hardening de comunicação interna para produção.
2. Definir retenção e rotina de revisão dos artifacts de logs/resultados do workflow `qa-e2e-manual.yml`.

---

## 7) Conclusão

O projeto está bem estruturado e com alta conformidade aos requisitos da análise, com implementação madura de microsserviços, mensageria e CI/CD. Após os ajustes desta rodada, os principais pontos críticos de contrato de status e tratamento de falhas de IA foram fechados; os próximos passos são de **maturidade arquitetural e operacional**.
