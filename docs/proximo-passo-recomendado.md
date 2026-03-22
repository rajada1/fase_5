# Próximo passo recomendado (runbook)

## Objetivo

Colocar o pipeline em produção usando autenticação OIDC e segredos no GitHub, sem chave estática da AWS.

## Pré-checagem rápida

1. Confirmar que a branch principal é `main`.
2. Confirmar que os workflows existem:
  - `.github/workflows/services-ci-cd.yml`
  - `.github/workflows/terraform-infra.yml`
3. Confirmar que os arquivos de policy existem:
   - `docs/iam/trust-policy.example.json`
   - `docs/iam/permissions-policy.example.json`
4. Confirmar configuração no GitHub:
  - Repository Variable: `DEPLOY_ENV` (ex.: `dev` ou `prod`)
  - Repository Secret: `AWS_REGION` (ex.: `us-east-1`)

## Passo 1 — Ajustar placeholders das policies

No arquivo `docs/iam/trust-policy.example.json`, substituir:
- `<ACCOUNT_ID>`
- `<OWNER>`
- `<REPO>`

No arquivo `docs/iam/permissions-policy.example.json`, substituir:
- `ACCOUNT_ID`

## Passo 2 — Criar provider OIDC (uma vez por conta)

Executar no AWS CLI:

aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1

Se já existir, ignorar erro de duplicidade.

## Passo 3 — Criar Role para GitHub Actions

Criar a role:

aws iam create-role \
  --role-name GitHubActionsDeployRole \
  --assume-role-policy-document file://docs/iam/trust-policy.example.json

Anexar permissões:

aws iam put-role-policy \
  --role-name GitHubActionsDeployRole \
  --policy-name GitHubActionsDeployInline \
  --policy-document file://docs/iam/permissions-policy.example.json

## Passo 4 — Configurar GitHub Secrets

No repositório GitHub (Settings > Secrets and variables > Actions), criar:

- AWS_ROLE_ARN
- DB_USERNAME
- DB_PASSWORD
- GEMINI_API_KEY
- COGNITO_USER_POOL_ID

Valor esperado de AWS_ROLE_ARN:

arn:aws:iam::SEU_ACCOUNT_ID:role/GitHubActionsDeployRole

## Passo 5 — Disparar deploy

1. Fazer commit e push na branch main.
2. Acompanhar o workflow no GitHub Actions.
3. Confirmar execução dos jobs:
  - `detect-changes`
  - job do(s) serviço(s) alterado(s)
  - `terraform-plan` (se houve mudança em `terraform/**`)
  - `terraform-apply` (somente em push na `main`)

## Passo 6 — Validar ambiente AWS

- ECR com imagens atualizadas (tag latest e SHA).
- ECS services com task definition nova.
- Recursos Terraform aplicados sem drift crítico.
- Gateway respondendo health check.

## Critério de sucesso

Todos os jobs verdes no GitHub Actions e serviços ativos no ECS com OIDC como padrão e fallback opcional por AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY via GitHub Secrets.

## Se falhar

- Falha em assumir role: revisar trust policy (sub do repositório/branch).
- Falha no terraform apply: revisar secrets DB_USERNAME, DB_PASSWORD, GEMINI_API_KEY, COGNITO_USER_POOL_ID.
- Falha no push ECR: revisar permissões ECR e iam:PassRole na policy inline.
