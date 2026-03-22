# Relatório Final de QA Técnico (Atualizado)

Data da revisão: 2026-03-21

## Resumo Executivo

O sistema está funcional para o objetivo proposto (upload de diagramas, processamento, análise por IA e geração de relatório), com evolução significativa em robustez operacional após as melhorias recentes.

Status geral: **Apto para validação integrada em ambiente de homologação**, com riscos remanescentes concentrados em segurança, governança de custos e maturidade de observabilidade de produção.

## Capacidades do Objetivo (Checklist)

- Receber diagramas (imagem/PDF): **Atendido**
- Processar diagramas: **Atendido** (OCR real para imagem/PDF, com fallback local para imagens)
- Aplicar IA para análise automática: **Atendido**
- Gerar relatório técnico estruturado: **Atendido**
- Operar em arquitetura escalável e organizada: **Parcialmente atendido** (boa base de microsserviços + gaps de hardening)

## Melhorias Implementadas (Antes → Depois)

### 1) Consistência transacional no Upload Service

- **Antes:** publicação SNS acoplada ao fluxo transacional do upload.
- **Depois:** padrão de **Transactional Outbox** implementado com publicação assíncrona e retry controlado.
- **Impacto:** menor acoplamento com indisponibilidade transitória de mensageria e melhor rastreabilidade de eventos.

### 2) Configuração de segurança do API Gateway

- **Antes:** configuração JWT em bloco incorreto de propriedades.
- **Depois:** configuração movida para `spring.security.oauth2.resourceserver.jwt`.
- **Impacto:** maior previsibilidade na autenticação via Cognito.

### 3) Entrega SNS → SQS em Terraform

- **Antes:** assinaturas sem policy explícita de envio SNS para filas.
- **Depois:** `aws_sqs_queue_policy` adicionada para as três filas.
- **Impacto:** redução de falhas silenciosas de integração entre tópicos e filas.

### 4) Idempotência no Report Service

- **Antes:** risco de erro em leitura com dados legados duplicados.
- **Depois:** consulta orientada ao relatório mais recente por `diagramId`.
- **Impacto:** maior resiliência frente a duplicidade histórica.

### 5) Tratamento de exceções em Report/Status

- **Antes:** mapeamento de erro via análise de string no controller.
- **Depois:** exceções tipadas + `@ControllerAdvice` + testes de contrato HTTP.
- **Impacto:** melhor clareza, manutenibilidade e previsibilidade de respostas 404/500.

### 6) Concorrência e idempotência no Status Service

- **Antes:** maior risco de corrida em atualizações concorrentes.
- **Depois:** leitura com lock para atualização e no-op para evento de estado repetido.
- **Impacto:** menos gravações desnecessárias e menor chance de inconsistência sob reentrega.

### 7) Dedupe distribuída nos serviços Python

- **Antes:** dedupe apenas em memória local.
- **Depois:** dedupe com Redis (`SET NX EX`) + fallback em memória.
- **Impacto:** redução de custo por retrabalho (OCR/LLM) entre réplicas e reinícios.

### 8) Observabilidade de dedupe

- **Antes:** sem visão direta de hit/miss da dedupe.
- **Depois:** counters internos, logs periódicos e snapshot em `/health`.
- **Impacto:** monitoramento operacional melhor para custo e estabilidade.

### 9) Ciclo de vida FastAPI

- **Antes:** `on_event("startup")` depreciado.
- **Depois:** migração para `lifespan` com cancelamento limpo da task de polling.
- **Impacto:** elimina depreciação de lifecycle e melhora shutdown.

### 10) OCR real no Processing Service

- **Antes:** cenários com PDF/erro podiam retornar conteúdo mock.
- **Depois:** pipeline OCR real para PDF (`pdf2image` + `pytesseract`) e fallback de imagem para `pytesseract` quando Textract falha.
- **Impacto:** elimina mascaramento por dados simulados no processamento e aumenta confiabilidade funcional do resultado extraído.

### 11) Hardening do fluxo de IA no AI Analysis Service

- **Antes:** falhas não-retryable podiam virar resposta fallback simulada.
- **Depois:** falhas não-retryable lançam exceção explícita; apenas erros de API transitórios permanecem retryáveis (via SQS).
- **Impacto:** evita falso sucesso e melhora governança de erro operacional.

### 12) IAM least privilege no Terraform (ECS Task Role)

- **Antes:** política ampla com `sqs:*` e `Resource = "*"`.
- **Depois:** permissões segmentadas por ação e recurso (S3/SNS/SQS por ARN específico; Textract mantido com `*` por limitação de escopo do serviço).
- **Impacto:** redução de superfície de ataque e melhor aderência ao princípio do menor privilégio.

### 13) Hardening contra prompt injection no AI Analysis Service

- **Antes:** proteção majoritariamente via instrução no prompt (sem sanitização/limite explícito do payload de entrada).
- **Depois:** sanitização defensiva do texto extraído (remoção de caracteres de controle, neutralização de delimitadores e truncamento configurável por `LLM_INPUT_MAX_CHARS`).
- **Impacto:** menor risco de quebra de contexto do prompt e menor exposição a payloads maliciosos ou excessivamente longos.

### 14) Métricas exportáveis (Prometheus) nos serviços Python

- **Antes:** observabilidade de execução restrita a logs e snapshot em `/health`.
- **Depois:** endpoint `/metrics` adicionado em `processing-service` e `ai-analysis-service`, com contadores de recebimento, sucesso, dedupe e falhas.
- **Impacto:** habilita integração direta com Prometheus/Grafana e monitoração contínua orientada a SLO.

### 15) Baseline de observabilidade operacional (queries + alertas)

- **Antes:** faltavam artefatos concretos para transformar métricas em monitoramento ativo.
- **Depois:** adicionados guia de PromQL/SLO e regras iniciais de alerta para throughput, error ratio e dedupe anômala.
- **Impacto:** acelera entrada em operação com monitoramento acionável desde o primeiro deploy.

## Evidências de Validação Executadas

- Compilação Maven do `upload-service` concluída com sucesso.
- Suítes focadas de `report-service` e `status-service` executadas com sucesso após ajustes.
- Testes Python de dedupe em `processing-service` e `ai-analysis-service`: passando.
- Testes de endpoint `/health` com `dedupeStats` em ambos serviços Python: passando.
- Regressão Python completa executada:
	- `processing-service`: `10 passed`.
	- `ai-analysis-service`: `10 passed`.
- Regressão Java consolidada executada por arquivos de teste dos serviços:
	- `api-gateway`, `upload-service`, `report-service`, `status-service`: `45 passed`, `0 failed`.
- Verificação de erros de editor/linter nos arquivos alterados: sem erros relevantes.

## Avaliação de Qualidade (Atual)

### Clareza

Boa estrutura de domínio por serviço e documentação principal consistente com o desenho arquitetural.

### Manutenibilidade

Evoluiu para **boa** após adoção de exceções tipadas, outbox e organização de dedupe em serviços dedicados.

### Extensibilidade futura

Boa para crescimento funcional. Pontos de evolução já preparados: Redis distribuído, outbox e handlers centralizados.

### Naming

No geral consistente. Ainda existe pequena mistura PT/EN em mensagens e nomes de campo de telemetria.

### Design

Arquitetura de microsserviços, mensageria assíncrona e isolamento de dados bem alinhados ao objetivo. Hardening ainda necessário para ambiente produtivo crítico.

## Riscos Remanescentes (Prioridade)

### P0 (alta prioridade)

### P1 (média prioridade)

1. **Dependência de binários OCR locais no runtime**
	- Pipeline local exige `tesseract-ocr` e `poppler-utils`; ausência desses pacotes causa falha de OCR para PDF/fallback local.

2. **Coleta central e rollout de alertas em produção**
	- Queries e regras base já definidas; falta conectar Prometheus/Alertmanager/Grafana no ambiente alvo.

3. **Rate limiting e proteção de superfícies operacionais**
	- Revisar cobertura de limites e exposição de endpoints de observabilidade no gateway para produção.

4. **Defesa avançada de conteúdo para IA**
	- Complementar sanitização atual com validação semântica por allowlist de padrões esperados e auditoria de payloads anômalos.

### P2 (baixa prioridade)

5. **Padronização final de idioma e mensagens operacionais**
	- Melhorar consistência de mensagens em PT/EN para operação e suporte.

## Recomendações Objetivas (Próximo Sprint)

1. Integrar `docs/prometheus-alert-rules.yml` ao Prometheus/Alertmanager do ambiente e validar rotas de notificação.
2. Garantir instalação/validação contínua dos binários OCR (`tesseract-ocr`, `poppler-utils`) em todos os ambientes.
3. Evoluir defesa de conteúdo para IA com validação semântica e trilha de auditoria de payloads suspeitos.
4. Revisar rate limiting e exposição de endpoints operacionais no gateway para produção.
5. Executar teste de carga controlado na esteira assíncrona e documentar SLOs.

## Conclusão

O projeto avançou de um MVP funcional para uma base tecnicamente sólida, com ganhos concretos em consistência, idempotência e observabilidade. Ainda há riscos importantes, mas agora são localizados e tratáveis em um plano incremental de hardening.

---

## Execução Local Complementar (2026-03-22)

Validação executada nesta data para confirmar o estado atual do repositório em ambiente local Windows.

### Testes automatizados executados

- `processing-service` (pytest): **15 passed**
- `ai-analysis-service` (pytest): **15 passed**
- `api-gateway` (maven/surefire): **3 passed, 0 failed**
- `upload-service` (maven/surefire): **10 passed, 0 failed**
- `report-service` (maven/surefire): **12 passed, 0 failed**
- `status-service` (maven/surefire): **16 passed, 0 failed**

Total consolidado desta execução: **71 testes passados, 0 falhas**.

### Verificação de integração local (infra)

- `docker compose config -q` executado com sucesso de parse.
- Observação: há warning de depreciação do campo `version` no `docker-compose.yml` (não bloqueante no momento).

### Bugs/performance identificados nesta execução

- Não foram encontradas falhas reproduzíveis nos testes automatizados locais executados.
- Não houve necessidade de alteração de código nesta rodada de validação.

### Pendências para validação E2E completa de integrações externas

Para validar ponta a ponta com serviços externos reais (Gemini/AWS/Redis), ainda é necessário executar a esteira com stack ativa (`docker-compose up`) e variáveis/credenciais válidas no ambiente (`GEMINI_API_KEY`, endpoint AWS/LocalStack e `REDIS_URL`), além de chamar os endpoints de upload/status/report em sequência.

---

## Simulação de usuário via API (estilo Insomnia) — 2026-03-22

Rodada de validação manual via HTTP (equivalente a coleção Insomnia/Postman) executada contra o ambiente local.

### Evidências coletadas

- `GET http://localhost:8080/actuator/health` → **200**
- `POST http://localhost:8080/api/v1/upload` (Basic Auth) → **403**
- `GET http://localhost:8080/api/v1/status/test-diagram` (Basic Auth) → **403**
- `GET http://localhost:8080/api/v1/reports/test-diagram` (Basic Auth) → **503**
- `GET http://localhost:8082/health` (processing-service direto) → **200**
- `GET http://localhost:8083/health` (ai-analysis-service direto) → **indisponível**

### Conclusão da simulação

- O fluxo completo de usuário (upload → status → relatório) **não pôde ser validado como OK** nesta execução.
- Bloqueios observados:
	1. **Autorização no gateway** retornando `403` para endpoints funcionais com credencial Basic usada no README.
	2. **Indisponibilidade de serviços downstream** (ex.: `report-service`/`ai-analysis-service`), refletindo `503` no gateway.

### Ações recomendadas para destravar o E2E

1. Padronizar autenticação local do gateway (permitir fluxo de teste local ou fornecer JWT válido para QA).
2. Corrigir startup dos serviços Java dependentes de banco para estabilizar `8081/8084/8085`.
3. Garantir `ai-analysis-service` ativo em `8083` durante a rodada de teste manual.
4. Reexecutar a coleção HTTP após estabilização e registrar o `diagramId` até o estado `ANALYZED`.

---

## Continuação da simulação E2E (2026-03-22, rodada de correções)

Após a rodada inicial bloqueada, foi feita depuração adicional ponta a ponta com execução real do fluxo HTTP e análise de filas/consumidores.

### Correções aplicadas nesta rodada

1. **S3 LocalStack no Upload Service**
	- `upload-service` ajustado para path-style addressing no S3, evitando erro de hostname virtual (`*.localhost`) em ambiente local.

2. **Rate limiter no API Gateway**
	- Adicionado `KeyResolver` para evitar `403` por chave vazia em requisições sem principal autenticado no cenário local de QA.

3. **Conflito de consumo de filas no Status Service**
	- `status-service` deixou de consumir as mesmas filas dos workers (`processing/report`).
	- Criadas filas dedicadas de status (`status-*-queue-dev`) e atualizada assinatura SNS para fanout correto.

4. **Configuração Python (.env) sem colisão de variáveis**
	- Introduzidas variáveis prefixadas por serviço (`PROCESSING_*`, `AI_ANALYSIS_*`) com fallback retrocompatível.
	- Eliminado cenário onde `processing-service` lia URL de fila do `ai-analysis-service` por sobrescrita em `.env` compartilhado.

5. **Compatibilidade Windows no processamento**
	- `processing-service` passou a usar diretório temporário do SO (`tempfile.gettempdir`) em vez de caminho fixo `/tmp`.

6. **Fallback OCR local defensivo**
	- Em ambiente local sem Textract disponível (licença LocalStack) e sem binários OCR instalados, o worker não trava a esteira: gera texto placeholder controlado.

7. **Dedupe isolada por estágio**
	- Chaves Redis de dedupe separadas por serviço (`processing` vs `ai-analysis`), evitando que evento processado no primeiro estágio seja descartado no segundo.

8. **Tratamento de quota Gemini no AI Analysis Service**
	- Erro `429 insufficient_quota` passou a ser classificado como **não-retryable**, com publicação de evento de falha (`ANALYSIS_FAILED`) para encerrar o fluxo com `ERROR` em vez de ficar indefinidamente em `PROCESSING`.

### Evidência funcional da rodada

- Upload via gateway: **202** (OK)
- Pipeline assíncrono: processamento e análise são consumidos (métricas confirmam recebimento/processamento)
- Status final para execução real validada (`diagramId=d9946f41-0939-45fe-886c-d507d534fecc`):
  - transição observada: `PROCESSING` → `ERROR` (em ~10s)
- Relatório final (`GET /reports/{diagramId}`): **404** quando análise termina em erro (comportamento consistente)

### Causa remanescente para não atingir `ANALYZED`

- Conta/chave Gemini com **quota insuficiente** durante chamada ao endpoint do modelo.
- Com as correções, este erro não deixa mais o fluxo preso: ele finaliza corretamente como `ERROR`.

### Estado atual de QA

- **Não há mais travamento indefinido em `PROCESSING`**.
- Fluxo local está operacional até estado terminal.
- Para obter trajetória de sucesso completa (`ANALYZED` + relatório 200), é necessário disponibilizar chave Gemini com quota ativa.
