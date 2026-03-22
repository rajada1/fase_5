# Setup OIDC GitHub Actions -> AWS (produção)

Este guia configura autenticação sem chave estática usando `AWS_ROLE_ARN` no GitHub Secrets.

## 1) Pré-requisitos

- Repositório GitHub com workflows em `.github/workflows/services-ci-cd.yml` e `.github/workflows/terraform-infra.yml`
- Conta AWS com permissão para IAM + ECR + ECS + Terraform
- Branch principal: `main`

## 2) Criar provider OIDC (uma vez por conta)

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

Se já existir, o comando retornará erro de duplicidade (pode ignorar).

## 3) Trust policy da Role (restrita ao seu repo)

Substitua `ACCOUNT_ID`, `OWNER` e `REPO`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:OWNER/REPO:ref:refs/heads/main"
        }
      }
    }
  ]
}
```

Arquivo pronto para copiar/ajustar no projeto:
- `docs/iam/trust-policy.example.json`

## 4) Política mínima de permissões da Role

Anexe políticas para:
- ECR push/pull (`ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`, etc.)
- ECS deploy (`ecs:RegisterTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeServices`)
- IAM pass role para task execution role (`iam:PassRole`)
- Terraform recursos usados no projeto (VPC, RDS, S3, SNS, SQS, ECS, ALB, CloudWatch)

Sugestão prática: começar com permissões amplas em ambiente de laboratório e depois reduzir por recurso ARN.

Arquivo base de permissões no projeto:
- `docs/iam/permissions-policy.example.json`

## 5) Criar Role

```bash
aws iam create-role \
  --role-name GitHubActionsDeployRole \
  --assume-role-policy-document file://docs/iam/trust-policy.example.json
```

Depois anexe a policy de permissões:

```bash
aws iam put-role-policy \
  --role-name GitHubActionsDeployRole \
  --policy-name GitHubActionsDeployInline \
  --policy-document file://docs/iam/permissions-policy.example.json
```

## 6) Configurar GitHub Secrets

No repositório (`Settings -> Secrets and variables -> Actions -> New repository secret`), criar:

- `AWS_ROLE_ARN`
- `AWS_REGION`
- `AWS_ACCESS_KEY_ID` (opcional, fallback)
- `AWS_SECRET_ACCESS_KEY` (opcional, fallback)
- `DB_USERNAME`
- `DB_PASSWORD`
- `GEMINI_API_KEY`
- `REDIS_URL`
- `COGNITO_USER_POOL_ID`

## 7) Validar no workflow

Os workflows já usam:
- `aws-actions/configure-aws-credentials@v4` com `role-to-assume`
- deploy por serviço no ECS ao alterar pastas de serviço
- `terraform plan/apply` separado quando alterar `terraform/**`
- `DEPLOY_ENV` via GitHub Variables (`vars`) e `workflow_dispatch`
- `AWS_REGION` via GitHub Secrets

Arquivos:
- `.github/workflows/services-ci-cd.yml`
- `.github/workflows/terraform-infra.yml`

## 8) Teste rápido

1. Faça push na `main`.
2. Verifique jobs:
  - `detect-changes`
  - job do(s) serviço(s) alterado(s) (`api-gateway`, `upload-service`, `processing-service`, `ai-analysis-service`, `report-service`, `status-service`)
  - `terraform-plan`
  - `terraform-apply` (apenas em `push` para `main`)
3. Confirme no AWS:
   - imagens no ECR
   - serviços ECS atualizados
   - recursos Terraform aplicados
