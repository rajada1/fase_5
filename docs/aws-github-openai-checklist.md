# Checklist de Configuração AWS + GitHub + OpenAI (Produção)

## 1) AWS (infra e segurança)

- [ ] Executar `terraform init` e `terraform apply` na pasta `terraform/`.
- [ ] Validar que os repositórios ECR existem com o sufixo de ambiente esperado (ex.: `-prod`).
- [ ] Criar/validar usuário e client no Cognito e preencher `COGNITO_USER_POOL_ID` no ambiente do gateway.
- [ ] Garantir permissões IAM mínimas para tasks ECS (S3, SNS, SQS, Textract).
- [ ] Habilitar HTTPS no ALB com ACM (listener 443 + certificado).

## 2) GitHub (Actions)

- [ ] Criar Role IAM para OIDC com trust policy para `token.actions.githubusercontent.com`.
- [ ] Conceder permissões da Role para ECR push (e deploy, se necessário).
- [ ] Criar secret `AWS_ROLE_ARN` no repositório.
- [ ] Criar secrets `AWS_REGION`, `DB_USERNAME`, `DB_PASSWORD`, `OPENAI_API_KEY`, `REDIS_URL`, `COGNITO_USER_POOL_ID` no repositório.
- [ ] Criar Repository Variable: `DEPLOY_ENV`.
- [ ] Confirmar branch protegida e required checks para `main`.
- [ ] Seguir o guia detalhado em `docs/github-oidc-setup.md`.

## 3) OpenAI

- [ ] Definir `OPENAI_API_KEY` no ambiente do `ai-analysis-service`.
- [ ] Definir `OPENAI_MODEL` (padrão atual: `gpt-4o-mini`).
- [ ] Configurar observabilidade para erro/custo de chamadas de LLM.

## 4) Variáveis por serviço

### processing-service
- `AWS_REGION`
- `AWS_ENDPOINT_URL` (vazio em produção)
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (ou IAM Role)
- `S3_BUCKET_NAME`
- `SQS_QUEUE_URL`
- `SNS_TOPIC_ARN`

### ai-analysis-service
- `AWS_REGION`
- `AWS_ENDPOINT_URL` (vazio em produção)
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (ou IAM Role)
- `SQS_QUEUE_URL`
- `SNS_TOPIC_ARN`
- `OPENAI_API_KEY`
- `OPENAI_MODEL`

### upload/report/status (Spring)
- `DB_HOST`
- `DB_NAME`
- `DB_USER`
- `DB_PASS`
- `AWS_REGION`
- `aws.endpoint` vazio em produção

### api-gateway (Spring)
- `AWS_REGION`
- `COGNITO_USER_POOL_ID`
- Valores injetados via Terraform com GitHub Secrets

## 5) Observações importantes

- O workflow de CI/CD usa OIDC como padrão; `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` podem ser usados como fallback via GitHub Secrets.
- O workflow de serviços executa somente para serviços com alteração de caminho no monorepo.
- O workflow de Terraform é separado e roda apenas quando há alteração em `terraform/**`.
- Dockerfiles foram adicionados para todos os serviços para viabilizar push no ECR.
