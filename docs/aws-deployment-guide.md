# Guia de Deploy na AWS

## Visão Geral da Arquitetura na AWS

```
                    Internet
                       │
                       ▼
              ┌─────────────────┐
              │   ALB (HTTP:80) │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │   API Gateway   │  (ECS Fargate)
              │   (port 8080)   │
              └──┬─────┬─────┬──┘
                 │     │     │
    ┌────────────┘     │     └────────────┐
    ▼                  ▼                  ▼
┌──────────┐    ┌──────────┐    ┌──────────┐
│  Upload  │    │  Report  │    │  Status  │
│ Service  │    │ Service  │    │ Service  │
│ (8081)   │    │ (8084)   │    │ (8085)   │
└────┬─────┘    └────▲─────┘    └────▲─────┘
     │               │               │
     ▼               │               │
┌─────────┐    ┌─────┴─────┐   ┌────┴──────┐
│   SNS   │    │    SQS    │   │    SQS    │
│ Topics  │───▶│  Queues   │   │  (fanout) │
└────┬────┘    └─────▲─────┘   └───────────┘
     │               │
     ▼               │
┌──────────┐    ┌────┴─────┐
│Processing│    │    AI    │
│ Service  │───▶│ Analysis │
│ (8082)   │    │ (8083)   │
└──────────┘    └──────────┘

Banco: RDS PostgreSQL (db.t3.micro)
Storage: S3 (diagramas)
Auth: Cognito
Service Discovery: AWS Cloud Map
```

## Custos Estimados (Free Tier)

| Serviço | Free Tier | Custo Estimado/mês |
|---------|-----------|-------------------|
| RDS db.t3.micro | 750h/mês (12 meses) | $0 |
| S3 | 5GB + 20k GET + 2k PUT | $0 |
| SQS | 1M requests/mês | $0 |
| SNS | 1M publishes/mês | $0 |
| ECR | 500MB storage | $0 |
| CloudWatch Logs | 5GB/mês | $0 |
| Cognito | 50k MAU | $0 |
| Cloud Map | DNS queries | ~$0.10 |
| **ECS Fargate** | **NÃO é free** | **~$50-70** |
| **ALB** | **NÃO é free** | **~$16** |
| **Total estimado** | | **~$70-90/mês** |

> **Nota**: ECS Fargate e ALB não fazem parte do Free Tier permanente. Para um hackathon de curta duração, o custo será proporcional ao tempo de uso. Lembre-se de fazer `terraform destroy` após a apresentação.

## Pré-requisitos

1. **Conta AWS** com Free Tier ativo
2. **AWS CLI** instalado e configurado
3. **Terraform** >= 1.5.0 instalado
4. **Docker** instalado
5. **Repositório GitHub** com Actions habilitado

## Passo 1: Configurar OIDC no GitHub

Para que o GitHub Actions possa fazer deploy na AWS sem credenciais estáticas:

### 1.1 Criar Identity Provider no IAM

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

### 1.2 Criar IAM Role para GitHub Actions

```bash
# Criar a trust policy (substitua GITHUB_ORG/REPO)
cat > trust-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:GITHUB_ORG/REPO:*"
        }
      }
    }
  ]
}
EOF

aws iam create-role \
  --role-name github-actions-deploy \
  --assume-role-policy-document file://trust-policy.json
```

### 1.3 Anexar Permissões à Role

```bash
# Permissões necessárias para deploy
cat > permissions-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:*",
        "ecs:*",
        "ec2:*",
        "elasticloadbalancing:*",
        "rds:*",
        "s3:*",
        "sns:*",
        "sqs:*",
        "iam:*",
        "logs:*",
        "cognito-idp:*",
        "servicediscovery:*",
        "route53:*"
      ],
      "Resource": "*"
    }
  ]
}
EOF

aws iam put-role-policy \
  --role-name github-actions-deploy \
  --policy-name deploy-permissions \
  --policy-document file://permissions-policy.json
```

## Passo 2: Configurar GitHub Secrets

No repositório GitHub, vá em **Settings > Secrets and variables > Actions** e adicione:

| Secret | Descrição | Exemplo |
|--------|-----------|---------|
| `AWS_ROLE_ARN` | ARN da role criada no passo 1 | `arn:aws:iam::123456789:role/github-actions-deploy` |
| `DB_USERNAME` | Usuário do RDS | `postgres` |
| `DB_PASSWORD` | Senha do RDS | `SenhaForte123!` |
| `GEMINI_API_KEY` | API key do Google Gemini | `AIza...` |

## Passo 3: Deploy da Infraestrutura

### Via GitHub Actions (recomendado)

1. Faça push de qualquer alteração na pasta `terraform/` para a branch `main`
2. O workflow `Terraform Infrastructure` será executado automaticamente
3. Ou execute manualmente via **Actions > Terraform Infrastructure > Run workflow**

### Via CLI local (alternativa)

```bash
cd terraform

# Copiar e preencher variáveis
cp terraform.tfvars.example terraform.tfvars
# Editar terraform.tfvars com seus valores

# Inicializar
terraform init

# Planejar
terraform plan

# Aplicar
terraform apply
```

## Passo 4: Inicializar o Banco de Dados

Após o RDS estar disponível, conecte-se e crie os bancos lógicos:

```bash
# Obter endpoint do RDS
RDS_ENDPOINT=$(terraform -chdir=terraform output -raw rds_endpoint)

# Conectar e criar bancos (use o password definido)
psql -h $RDS_ENDPOINT -U postgres -c "CREATE DATABASE upload_db;"
psql -h $RDS_ENDPOINT -U postgres -c "CREATE DATABASE report_db;"
psql -h $RDS_ENDPOINT -U postgres -c "CREATE DATABASE status_db;"
```

> **Nota**: Para conectar ao RDS em subnet privada, use um bastion host ou Session Manager. Alternativamente, temporariamente torne o RDS público para a configuração inicial.

## Passo 5: Deploy dos Serviços

### Via GitHub Actions (recomendado)

1. Faça push de alterações em qualquer serviço para `main`
2. O workflow detecta quais serviços mudaram e faz deploy apenas deles
3. Para forçar deploy de todos: **Actions > Services CI/CD > Run workflow > force_deploy_all: true**

### Primeiro deploy manual (necessário para criar imagens iniciais)

```bash
# Login no ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com

# Build e push de cada serviço
for service in api-gateway upload-service processing-service ai-analysis-service report-service status-service; do
  docker build -t ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/architecture/$service:latest ./$service
  docker push ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/architecture/$service:latest
done
```

## Passo 6: Verificar Deploy

```bash
# Obter URL do ALB
ALB_URL=$(terraform -chdir=terraform output -raw alb_dns_name)

# Testar health check
curl http://$ALB_URL/actuator/health

# Testar upload (com token Cognito)
curl -X POST http://$ALB_URL/api/v1/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@diagram.png"
```

## Passo 7: Criar Usuário no Cognito (para testes)

```bash
POOL_ID=$(terraform -chdir=terraform output -raw cognito_user_pool_id)
CLIENT_ID=$(terraform -chdir=terraform output -raw cognito_client_id)

# Criar usuário
aws cognito-idp admin-create-user \
  --user-pool-id $POOL_ID \
  --username testuser@example.com \
  --user-attributes Name=email,Value=testuser@example.com \
  --temporary-password TempPass123!

# Definir senha permanente
aws cognito-idp admin-set-user-password \
  --user-pool-id $POOL_ID \
  --username testuser@example.com \
  --password TestPass123! \
  --permanent

# Obter token
aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id $CLIENT_ID \
  --auth-parameters USERNAME=testuser@example.com,PASSWORD=TestPass123!
```

## Limpeza (IMPORTANTE!)

Para evitar cobranças após o hackathon:

```bash
# Destruir toda a infraestrutura
cd terraform
terraform destroy -auto-approve
```

Ou via GitHub Actions: **Actions > Terraform Infrastructure > Run workflow > action: destroy**

## Troubleshooting

### Serviço não inicia no ECS
- Verifique os logs no CloudWatch: `/ecs/<service-name>-dev`
- Verifique se a imagem existe no ECR
- Verifique se as variáveis de ambiente estão corretas na task definition

### Erro de conexão com RDS
- Verifique se o Security Group do ECS permite saída na porta 5432
- Verifique se o Security Group do RDS permite entrada do SG do ECS
- Verifique se os bancos lógicos foram criados

### Erro de permissão AWS (SQS/SNS/S3)
- Verifique a Task Role do ECS
- Verifique se os ARNs dos recursos estão corretos nas variáveis de ambiente

### Services não se comunicam
- Verifique se o Cloud Map está registrando os serviços
- Verifique se o Security Group do ECS permite tráfego `self` (inter-service)
