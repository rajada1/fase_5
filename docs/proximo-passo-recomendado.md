# Próximo Passo Recomendado (Runbook)

> **Atualizado em:** 2026-03-22  
> **Estado atual:** Ambiente local funcional (LocalStack + Docker Compose). Pipeline CI/CD configurado. Pendente: configuração de produção AWS.

---

## 📊 Status do Projeto

### ✅ Concluído

| Item | Detalhes |
|------|----------|
| **Arquitetura de microsserviços** | 6 serviços: `api-gateway`, `upload-service`, `processing-service`, `ai-analysis-service`, `report-service`, `status-service` |
| **Java 21** | Todos os serviços Java (Spring Boot) usando JDK 21 (Temurin) |
| **Python 3.11+** | `processing-service` e `ai-analysis-service` em Python 3.11+ |
| **Status em PT-BR** | Status padronizados em português: `Recebido`, `Em processamento`, `Analisado`, `Erro` |
| **Upload endpoint corrigido** | Retorna `400 Bad Request` quando nenhum arquivo é enviado (antes retornava 500) |
| **Validação MIME spoofing** | Upload rejeita arquivos com conteúdo incompatível com o tipo declarado (`400`) |
| **Ambiente local (Docker Compose)** | LocalStack (S3, SQS, SNS), Redis, PostgreSQL via `docker-compose.yml` |
| **CI/CD Github Actions** | `services-ci-cd.yml` — build, test e deploy por serviço alterado (monorepo) |
| **CI/CD Terraform** | `terraform-infra.yml` — plan/apply automático para `terraform/**` |
| **Pipeline E2E manual** | `qa-e2e-manual.yml` — workflow de QA disparado manualmente |
| **Observabilidade Prometheus** | Métricas expostas em `/metrics` para `processing-service` e `ai-analysis-service` |
| **Deduplicação Redis** | Deduplicação de mensagens SQS via Redis no `processing-service` e `ai-analysis-service` |
| **IAM / OIDC docs** | Políticas de exemplo em `docs/iam/trust-policy.example.json` e `docs/iam/permissions-policy.example.json` |
| **Testes QA** | Testes de upload, status, relatório, saúde dos serviços e métricas (scripts PowerShell + pytest) |

---

## 🔴 Pendente — Produção AWS

### Pré-checagem rápida

1. Confirmar que a branch principal é `main`.
2. Confirmar que os workflows existem:
   - `.github/workflows/services-ci-cd.yml` ✅
   - `.github/workflows/terraform-infra.yml` ✅
   - `.github/workflows/qa-e2e-manual.yml` ✅
3. Confirmar que os arquivos de policy existem:
   - `docs/iam/trust-policy.example.json` ✅
   - `docs/iam/permissions-policy.example.json` ✅
4. Ter acesso à conta AWS (via CLI autenticado).

---

### Passo 1 — Ajustar placeholders das policies IAM

No arquivo `docs/iam/trust-policy.example.json`, substituir:
- `<ACCOUNT_ID>` → ID numérico da conta AWS
- `<OWNER>` → usuário/organização GitHub
- `<REPO>` → nome do repositório

No arquivo `docs/iam/permissions-policy.example.json`, substituir:
- `ACCOUNT_ID` → ID numérico da conta AWS

---

### Passo 2 — Criar provider OIDC (uma vez por conta)

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

Se já existir, ignorar erro de duplicidade.

---

### Passo 3 — Criar Role IAM para GitHub Actions

```bash
aws iam create-role \
  --role-name GitHubActionsDeployRole \
  --assume-role-policy-document file://docs/iam/trust-policy.example.json

aws iam put-role-policy \
  --role-name GitHubActionsDeployRole \
  --policy-name GitHubActionsDeployInline \
  --policy-document file://docs/iam/permissions-policy.example.json
```

---

### Passo 4 — Configurar GitHub Secrets e Variables

No repositório GitHub (**Settings → Secrets and variables → Actions**):

**Secrets** (sensíveis):
| Nome | Valor |
|------|-------|
| `AWS_ROLE_ARN` | `arn:aws:iam::<ACCOUNT_ID>:role/GitHubActionsDeployRole` |
| `AWS_REGION` | ex.: `us-east-1` |
| `DB_USERNAME` | Usuário do banco de dados RDS |
| `DB_PASSWORD` | Senha do banco de dados RDS |
| `GEMINI_API_KEY` | Chave da API Google Gemini |
| `COGNITO_USER_POOL_ID` | ID do User Pool do Amazon Cognito |
| `REDIS_URL` | URL de conexão Redis (ElastiCache em produção) |

**Variables** (não-sensíveis):
| Nome | Valor |
|------|-------|
| `DEPLOY_ENV` | `prod` (ou `dev` para staging) |

---

### Passo 5 — Provisionar infraestrutura com Terraform

```bash
cd terraform/
terraform init
terraform plan -var="db_username=$DB_USERNAME" -var="db_password=$DB_PASSWORD"
terraform apply  # confirmar com 'yes'
```

Validar após apply:
- Repositórios ECR criados com sufixo `-prod` (ou `-dev`)
- Cluster ECS `architecture-cluster-prod` ativo
- Tasks ECS registradas para cada serviço
- Bucket S3 `architecture-diagrams-prod`
- Filas SQS e tópico SNS criados
- Banco RDS acessível pelas tasks ECS
- Cognito User Pool com App Client configurado

---

### Passo 6 — Disparar deploy via CI/CD

1. Fazer commit e push na branch `main`.
2. Acompanhar o workflow no **GitHub Actions**.
3. Confirmar execução dos jobs:
   - `detect-changes`
   - Jobs dos serviços alterados (`upload-service`, `api-gateway`, etc.)
   - `terraform-plan` (se houve mudança em `terraform/**`)
   - `terraform-apply` (somente em push na `main`)

---

### Passo 7 — Validar ambiente AWS pós-deploy

- [ ] ECR com imagens atualizadas (tag `latest` e SHA do commit).
- [ ] ECS services com task definition nova e status `RUNNING`.
- [ ] Prometheus/CloudWatch com métricas dos serviços.
- [ ] API Gateway respondendo health check em `/health`.
- [ ] Fluxo E2E: upload → SQS → processing → SQS → AI analysis → SNS → report → GET status = `Analisado`.
- [ ] Recursos Terraform sem drift crítico (`terraform plan` retorna sem mudanças).

---

## 🟡 Pendente — Melhorias técnicas identificadas

| Prioridade | Item | Onde |
|------------|------|------|
| 🔴 Alta | Upload sem arquivo retornando `500` em ambiente local (fix pendente) | `upload-service/UploadController.java` |
| 🟡 Média | Configurar HTTPS no ALB com certificado ACM | `terraform/` |
| 🟡 Média | Adicionar autenticação Cognito no `api-gateway` para todos os endpoints | `api-gateway/` |
| 🟡 Média | Adicionar Prometheus scrape config para `upload-service`, `report-service` e `status-service` | `docs/observability-prometheus.md` |
| 🟢 Baixa | Adicionar integração do Prometheus com Grafana no `docker-compose.yml` | `docker-compose.yml` |
| 🟢 Baixa | Configurar alertas no `docs/prometheus-alert-rules.yml` no ambiente de produção | AWS CloudWatch / Alertmanager |

---

## Critério de sucesso

- Todos os jobs **verdes** no GitHub Actions.
- Todos os 6 serviços **ativos no ECS** com health check ok.
- Fluxo completo E2E funcional em produção (upload → relatório).
- OIDC como método principal de autenticação AWS no CI/CD.

---

## Se falhar

| Sintoma | Ação |
|---------|------|
| Falha ao assumir role OIDC | Revisar `trust-policy.json` — `sub` deve corresponder ao repositório e branch exatos |
| Falha no `terraform apply` | Verificar secrets `DB_USERNAME`, `DB_PASSWORD`, `GEMINI_API_KEY`, `COGNITO_USER_POOL_ID` |
| Falha no push para ECR | Revisar permissões ECR e `iam:PassRole` na policy inline da role |
| Task ECS não inicia (`STOPPED`) | Ver logs no CloudWatch `ecs/architecture-cluster-prod` — geralmente variável de ambiente ausente |
| Upload retorna 500 | Verificar que o campo `file` está sendo enviado como `multipart/form-data` |
| Status não atualiza | Verificar que SQS está disponível e que `processing-service` está consumindo a fila |
