# Code Review Técnico — Projeto de Análise de Diagramas Arquiteturais

Data: 2026-03-21

## 1) Resumo Executivo

O projeto apresenta uma base **boa e funcional** para o objetivo proposto, com arquitetura de microsserviços, esteira assíncrona via SNS/SQS, IA integrada ao fluxo e pipelines CI/CD completos.

Status geral da revisão: **Aprovado com ressalvas**.

Principais pontos positivos:
- Fluxo ponta a ponta implementado (upload → processamento OCR → análise IA → relatório → consulta de status).
- Boas práticas relevantes já aplicadas (Transactional Outbox no upload, idempotência/dedupe em serviços Python, circuit breaker e rate limit no gateway).
- Testes automatizados existentes e executáveis por serviço.

Principais lacunas:
- Nem todos os serviços possuem banco próprio (requisito técnico estrito não atendido para serviços Python).
- Padronização final de nomenclatura de tracing (`traceId` vs `correlationId`) ainda pode ser unificada.

---

## 2) Aderência aos Requisitos Funcionais

### 2.1 Upload de diagrama (imagem/PDF)
**Status: Atendido**

Evidências:
- `upload-service` aceita `image/jpeg`, `image/png` e `application/pdf`.
- Validações de arquivo vazio, filename inválido/path traversal e content-type.

### 2.2 Criação do processo de análise
**Status: Atendido**

Evidências:
- Upload persiste metadado com status inicial `RECEIVED`.
- Publicação de evento assíncrono via outbox para disparar processamento.

### 2.3 Consulta de status do processamento
**Status: Atendido**

Evidências:
- Endpoint de status existe e funciona (`status-service`).
- Estados padronizados no fluxo: `RECEIVED` → `PROCESSING` → `ANALYZED`, com `ERROR` para eventos de falha (`*_FAILED`).

### 2.4 Geração de relatório com componentes, riscos e recomendações
**Status: Atendido**

Evidências:
- `ai-analysis-service` produz estrutura com `components`, `risks`, `recommendations`.
- `report-service` consome evento `ANALYSIS_COMPLETED`, persiste e expõe via endpoint REST.

---

## 3) Aderência aos Requisitos Técnicos

### 3.1 Arquitetura baseada em microsserviços
**Status: Atendido**

Serviços identificados: API Gateway, Upload, Processing, AI Analysis, Report, Status.

### 3.2 Comunicação REST + fluxo assíncrono
**Status: Atendido**

Evidências:
- REST no gateway e serviços de borda.
- Fluxo assíncrono em SNS/SQS entre upload/processamento/análise/relatório/status.

### 3.3 Clean Architecture / Hexagonal
**Status: Parcialmente atendido**

Evidências:
- Serviços Java apresentam separação por camadas (`application`, `domain`, `infrastructure`, `interfaces`).
- Serviços Python têm boa organização modular, porém sem a mesma explicitude de fronteiras hexagonais.

### 3.4 Responsabilidade clara por serviço
**Status: Atendido**

### 3.5 Banco de dados próprio por serviço
**Status: Parcialmente atendido**

Evidências:
- `upload-service`, `report-service` e `status-service` usam bancos lógicos dedicados (`upload_db`, `report_db`, `status_db`).
- `processing-service` e `ai-analysis-service` não possuem persistência própria (apenas integração com S3/SQS/SNS e Redis opcional para dedupe).

### 3.6 Testes automatizados por serviço
**Status: Atendido com ressalvas**

Evidências:
- Java: testes em `api-gateway`, `upload-service`, `report-service`, `status-service`.
- Python: testes em `processing-service` e `ai-analysis-service`.

Ressalva:
- Não há testes automatizados para execução integrada end-to-end do pipeline completo.

---

## 4) IA no Fluxo, Segurança e Guardrails

### 4.1 IA integrada ao fluxo sistêmico
**Status: Atendido**

Evidências:
- IA é acionada por evento assíncrono do `processing-service`.
- Resultado da IA é publicado em evento e persistido no `report-service`.

### 4.2 Guardrails de entrada/saída
**Status: Atendido (nível bom para MVP)**

Evidências:
- Sanitização de entrada (`control chars`, neutralização de delimitadores, truncamento por limite).
- Instrução anti prompt injection no system prompt.
- Resposta exigida em JSON (`response_format: json_object`) e validação por modelo Pydantic.

### 4.3 Tratamento de falhas de IA
**Status: Atendido com ressalvas**

Evidências:
- Erros transitórios da API da Gemini são tratados como retryáveis (reentrega SQS).
- Erros não-retryables são diferenciados.
- Erros não-retryables e payloads inválidos geram evento `ANALYSIS_FAILED`, refletindo estado `ERROR` no `status-service`.

Ressalva:
- Mensagens sem `diagramId` não conseguem propagar erro por entidade de negócio (não há identificador para correlacionar).

### 4.4 Discussão de limitações do modelo
**Status: Não atendido de forma explícita na documentação principal**

Gap:
- README e docs descrevem operação e arquitetura, mas não detalham claramente limites de acurácia, viés, falso-positivo/negativo e fronteiras da análise IA.

---

## 5) Infraestrutura, CI/CD e Observabilidade

### 5.1 Docker / Docker Compose
**Status: Atendido**

Evidências:
- Dockerfiles por serviço.
- `docker-compose.yml` com LocalStack, Postgres e Redis.

### 5.2 CI/CD (build, testes e deploy)
**Status: Atendido**

Evidências:
- Workflow de serviços com build/test por serviço, build/push de imagem e deploy ECS.
- Workflow Terraform com `init`, `fmt`, `validate`, `plan` e `apply` em `main`.

### 5.3 Logs estruturados
**Status: Atendido**

Evidências:
- Pollers SQS Java e Python com logs correlacionáveis por `diagramId/diagram_id`, `eventType/event_type` e `queueUrl/queue_url`.
- Endpoints REST críticos (`upload`, `status`, `reports`) com logs de entrada/saída contendo identificadores de negócio.
- `X-Correlation-Id` no fluxo REST e `correlationId` propagado no pipeline assíncrono (upload → processing → ai-analysis → report/status).

Gap:
- Consolidar padrão definitivo de chave de rastreio entre squads (`traceId`/`correlationId`) e dashboards.

### 5.4 Tratamento de erros
**Status: Atendido com pontos de melhoria**

Pontos fortes:
- Handlers e testes para cenários de erro nos serviços Java.
- Estratégias de retry e dedupe em pontos críticos.

Ponto de atenção:
- Consolidar monitoramento de DLQ/retentativas com alertas no ambiente produtivo.

### 5.5 README explicativo
**Status: Atendido**

---

## 6) Evidências de Validação Executadas na Revisão

Execuções realizadas no ambiente local:
- `processing-service`: `python -m pytest -q` → **13 passed**.
- `ai-analysis-service`: `python -m pytest -q` → **13 passed**.
- `status-service`: `mvn -q test` → **exit code 0**.
- `report-service`: `mvn -q test` → **exit code 0**.
- `upload-service`: `mvn -q test` → **exit code 0**.
- `api-gateway`: `mvn -q test` → **exit code 0**.

Adições validadas nesta rodada:
- Testes de fluxo dos pollers Python (sucesso, payload inválido e falha não-retryable) em ambos os serviços.
- Hardening de descarte de payload malformado no poller do `report-service`.
- Logs de correlação em pollers SQS e endpoints REST Java.

---

## 7) Achados Prioritários

### P0 (Alta)
1. **Padronizar e fechar máquina de estados de negócio**
   - **Concluído**: estados unificados para `RECEIVED` → `PROCESSING` → `ANALYZED` e `ERROR` para falhas.

2. **Garantir tratamento de mensagens inválidas/venenosas em todos os pollers**
   - **Concluído**: payloads malformados/inválidos são descartados explicitamente, reduzindo risco de poison loop.

### P1 (Média)
3. **Fechar requisito “banco por serviço”**
   - Formalizar exceção arquitetural para serviços stateless ou introduzir persistência mínima onde necessário.

4. **Evoluir logs para formato estruturado com correlação**
   - **Concluído**: correlação de negócio e de requisição aplicada em REST e mensageria (`X-Correlation-Id`/`correlationId`).

5. **Documentar limitações da IA e critérios de qualidade**
   - Limites de interpretação de diagramas, taxa de erro esperada, casos fora de escopo.

### P2 (Baixa)
6. **Adicionar testes de integração end-to-end da esteira assíncrona**
   - Cobrir o fluxo completo em ambiente local com LocalStack.

---

## 8) Recomendações Objetivas (Próximo Sprint)

1. Padronizar nomenclatura final do identificador de rastreio (`traceId`/`correlationId`) em métricas, logs e contratos.
2. Integrar alertas de DLQ e retentativas com Prometheus/Alertmanager em ambiente alvo.
3. Definir ADR para “serviços stateless sem DB próprio” ou adicionar persistência mínima por serviço.
4. Finalizar padrão único de logging estruturado entre serviços (campos e formato homogêneos).
5. Incluir seção formal “Limitações do Modelo de IA” no README/docs.
6. Criar suíte de teste integrada do pipeline completo.

---

## 9) Conclusão

O projeto está tecnicamente consistente e operacional para o objetivo acadêmico/prototipação avançada, com vários elementos de engenharia já maduros (outbox, dedupe, CI/CD por serviço, IaC). Para produção com menor risco, os próximos passos devem priorizar **consistência de estado**, **resiliência de mensageria para payload inválido**, **logs estruturados** e **governança explícita de limitações da IA**.
