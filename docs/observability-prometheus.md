# Observabilidade com Prometheus (Processing + AI Analysis)

Este guia consolida consultas PromQL e indicadores operacionais para os endpoints `/metrics` dos serviços Python:

- `processing-service` (porta `8082`)
- `ai-analysis-service` (porta `8083`)

## Correlação de logs (Java + Python)

Além das métricas, os serviços foram padronizados para registrar campos de correlação em logs operacionais.

### Campos de correlação adotados

- `diagramId` (canônico; `diagram_id` mantido temporariamente por compatibilidade)
- `eventType` (canônico; `event_type` mantido temporariamente por compatibilidade)
- `queueUrl` (canônico; `queue_url` mantido temporariamente por compatibilidade)
- `correlationId` (canônico; `correlation_id` mantido temporariamente por compatibilidade)
- `state`/`targetState` para transições de status

### Pontos principais com correlação

- `status-service` (SQS Poller + endpoint GET de status)
- `report-service` (SQS Poller + endpoint GET de relatório)
- `upload-service` (endpoint POST de upload)
- `processing-service` e `ai-analysis-service` (pollers Python)

### Exemplos de filtros operacionais

Buscar eventos de um diagrama específico:

```bash
grep -R "diagramId=diag-123\|diagram_id=diag-123" logs/
```

Buscar falhas de parsing de mensagens de fila:

```bash
grep -R "reason=malformed_payload" logs/
```

Buscar transições para erro terminal:

```bash
grep -R "targetState=ERROR\|eventType=.*_FAILED\|event_type=.*_FAILED" logs/
```

## Métricas disponíveis

### Processing Service

- `processing_messages_received_total`
- `processing_messages_processed_total`
- `processing_messages_deduped_total`
- `processing_messages_failed_total`

### AI Analysis Service

- `ai_analysis_messages_received_total`
- `ai_analysis_messages_processed_total`
- `ai_analysis_messages_deduped_total`
- `ai_analysis_messages_failed_total`

## Consultas PromQL recomendadas

### Taxa de mensagens recebidas (por segundo, janela de 5 min)

```promql
sum(rate(processing_messages_received_total[5m]))
sum(rate(ai_analysis_messages_received_total[5m]))
```

### Taxa de sucesso (mensagens processadas)

```promql
sum(rate(processing_messages_processed_total[5m]))
sum(rate(ai_analysis_messages_processed_total[5m]))
```

### Taxa de erro

```promql
sum(rate(processing_messages_failed_total[5m]))
sum(rate(ai_analysis_messages_failed_total[5m]))
```

### Taxa de deduplicação

```promql
sum(rate(processing_messages_deduped_total[5m]))
sum(rate(ai_analysis_messages_deduped_total[5m]))
```

### Error ratio (aprox.)

```promql
sum(rate(processing_messages_failed_total[5m]))
/
clamp_min(sum(rate(processing_messages_received_total[5m])), 0.0001)
```

```promql
sum(rate(ai_analysis_messages_failed_total[5m]))
/
clamp_min(sum(rate(ai_analysis_messages_received_total[5m])), 0.0001)
```

### Success ratio (aprox.)

```promql
sum(rate(processing_messages_processed_total[5m]))
/
clamp_min(sum(rate(processing_messages_received_total[5m])), 0.0001)
```

```promql
sum(rate(ai_analysis_messages_processed_total[5m]))
/
clamp_min(sum(rate(ai_analysis_messages_received_total[5m])), 0.0001)
```

## SLO inicial sugerido

- Disponibilidade do pipeline assíncrono: `>= 99%` de success ratio em janela de 30 min.
- Error ratio: `< 2%` sustentado por 10 min.
- Deduplicação: monitorar desvios bruscos (pico pode indicar reentrega excessiva).

## Runbook rápido (incidentes)

1. Se `failed_total` subir e `processed_total` cair, verificar credenciais/endpoint AWS e conectividade de filas.
2. Se `deduped_total` disparar, investigar reentregas SQS e latência dos consumidores.
3. Se `received_total` zerar inesperadamente, validar publicação SNS e subscriptions SNS->SQS.
4. Correlacionar com logs JSON dos serviços para identificar `diagramId` impactado.
