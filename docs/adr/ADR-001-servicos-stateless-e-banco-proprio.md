# ADR-001 — Banco de dados por serviço e exceção para serviços stateless

- **Status:** Aceita
- **Data:** 2026-03-22
- **Contexto:** Requisito técnico do projeto solicita “cada serviço deve ter banco de dados próprio”.

## Contexto

A solução possui 6 serviços:

- `upload-service`
- `processing-service`
- `ai-analysis-service`
- `report-service`
- `status-service`
- `api-gateway`

Atualmente:

- `upload-service`, `report-service` e `status-service` possuem banco lógico próprio (`upload_db`, `report_db`, `status_db`).
- `processing-service` e `ai-analysis-service` são **stateless** e orientados a eventos (SQS/SNS), usando armazenamento externo operacional (S3 e Redis para dedupe).
- `api-gateway` atua como roteador/BFF e não mantém estado de domínio.

## Decisão

Adotar a seguinte interpretação arquitetural para o requisito:

1. Serviços com **estado de domínio persistente** devem possuir banco próprio (já atendido por `upload`, `report`, `status`).
2. Serviços **stateless** de processamento (`processing`, `ai-analysis`) podem operar sem banco relacional próprio, desde que:
   - não sejam fonte de verdade de dados de negócio;
   - tenham idempotência/deduplicação;
   - publiquem eventos de erro/sucesso para rastreabilidade;
   - mantenham observabilidade e correlação de execução.
3. `api-gateway` não possui banco por ser componente de borda e roteamento.

## Justificativa

- Evita persistência redundante e acoplamento desnecessário em serviços de execução transitória.
- Mantém separação clara entre serviços de **estado de negócio** e serviços de **orquestração/processamento**.
- Preserva escalabilidade horizontal dos workers sem overhead de esquema relacional local.

## Consequências

### Positivas

- Menor complexidade operacional nos serviços de processamento.
- Escalabilidade e recuperação mais simples para workers stateless.
- Menor custo de manutenção de schema/migração para serviços sem estado de domínio.

### Riscos

- Interpretação do requisito pode ser vista como parcial em avaliações estritamente literais.
- Auditoria histórica detalhada de processamento depende mais de eventos/logs e menos de banco local no worker.

## Mitigações

- Documentar explicitamente esta decisão (esta ADR) e referenciá-la no relatório técnico.
- Manter persistência do resultado final e do status em serviços de domínio (`report` e `status`).
- Garantir observabilidade com métricas (`/metrics`) e logs estruturados com correlação.

## Alternativas consideradas

1. **Criar banco próprio para `processing-service` e `ai-analysis-service`**
   - Prós: aderência literal ao requisito.
   - Contras: aumenta complexidade e custo sem benefício claro para domínio.

2. **Persistência mínima apenas para trilha de execução**
   - Prós: aumenta auditoria local.
   - Contras: duplica responsabilidade já coberta por status/report/logs/eventos.

## Referências

- `init-dbs.sql`
- `docs/architecture.md`
- `code_review.md`
- `docs/command/analise.md`
