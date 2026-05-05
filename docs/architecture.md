# Visão Geral da Arquitetura

Este diagrama representa o fluxo e a arquitetura para análise de diagramas de arquitetura de software usando IA. O sistema foi projetado em uma arquitetura de microsserviços, totalmente hospedado na AWS utilizando uma abordagem conteinerizada (ECS Fargate), comunicando-se via REST para tarefas síncronas e SQS/SNS para tarefas assíncronas. O repositório segue o padrão **monorepo**, com pipelines de CI/CD orientados por mudança de caminho para build, teste e deploy por serviço.

## Fluxo do Sistema

1. **Requisição do Cliente**: Um usuário envia uma imagem ou PDF de um diagrama.
2. **API Gateway**: A requisição chega no ponto de entrada único (Spring Cloud Gateway), que a roteia para o *Upload Service*.
3. **Upload Service**: 
   - Recebe o arquivo.
   - Salva-o em um **Bucket do Amazon S3**.
   - Armazena os metadados no **RDS (PostgreSQL)**.
   - Publica um `FileUploadedEvent` (Evento de Arquivo Enviado) em um **Tópico SNS**, que é roteado para o **Processing Service** via SQS.
4. **Processing Service**:
   - Escuta (Polls) a **Fila SQS**.
   - Baixa o arquivo do S3.
   - Extrai texto e componentes visuais usando OCR.
   - Publica um `DiagramProcessedEvent` (Evento de Diagrama Processado) no SNS/SQS.
5. **AI Analysis Service**:
   - Escuta a **Fila SQS** aguardando o `DiagramProcessedEvent`.
   - Utiliza um LLM (Modelo em Linguagem Grande) para analisar os dados de arquitetura.
   - Gera uma análise em JSON identificando riscos, pontos únicos de falha (POFs) e componentes ausentes.
   - Publica um `AnalysisCompletedEvent` (Evento de Análise Concluída) no SNS/SQS.
6. **Report Service**:
   - Consome o `AnalysisCompletedEvent`.
   - Gera o relatório final formatado e o armazena em seu próprio **banco de dados RDS**.
   - Expõe um endpoint GET para que os usuários recuperem o relatório.
7. **Status Service**:
   - Escuta os eventos de todos os serviços ao longo do fluxo de trabalho.
   - Rastreia o progresso (`RECEIVED` -> `PROCESSING` -> `ANALYZED` -> `ERROR`).
   - Armazena os estados de transição em seu próprio **banco de dados RDS**.

## Diagrama da Infraestrutura AWS (Representação Textual)

```text
                                               +-------------------+
                                               |                   |
                                       +------>| Amazon S3         |
                                       |       | (Raw & Processed) |
                                       |       |                   |
  +--------+      +-------------+      |       +-------------------+
  |        |      |             |      |
  | Client |----->| API Gateway |----->|
  |        |      |             |      |
  +--------+      +-------------+      |       +-------------------+
                         |             |       |                   |
                         |             +------>| Upload Service    |---> [RDS: upload_db]
                         |                     | (Spring Boot)     |
                         |                     +-------------------+
                         |                               |
                         |                     +-------------------+
                         |                     | SNS / SQS         | (FileUploadedEvent)
                         |                     +-------------------+
                         |                               |
                         |                     +-------------------+
                         |                     | Processing Service|
                         |                     | (FastAPI + OCR)   |
                         |                     +-------------------+
                         |                               |
                         |                     +-------------------+
                         |                     | SNS / SQS         | (DiagramProcessedEvent)
                         |                     +-------------------+
                         |                               |
                         |                     +-------------------+
                         |                     | AI Analysis Svc   |
                         |                     | (FastAPI + LLM)   |
                         |                     +-------------------+
                         |                               |
                         |                     +-------------------+
                         |                     | SNS / SQS         | (AnalysisCompletedEvent)
                         |                     +-------------------+
                         |                               |
                         |                     +-------------------+
                         +-------------------->| Report Service    |---> [RDS: report_db]
                         |                     | (Spring Boot)     |
                         |                     +-------------------+
                         |                                 
                         |                     +-------------------+
                         +-------------------->| Status Service    |---> [RDS: status_db]
                                               | (Spring Boot)     |
                                               +-------------------+
```

## Requisitos Não Funcionais Atendidos
* **Alta Disponibilidade (High Availability)**: Contêineres no ECS Fargate.
* **Resiliência**: O SQS fornece filas de mensagens mortas (dead-letter queues) e mecanismos de repetição (retry) caso um serviço dependente falhe.
* **Isolamento de Dados**: Padrão de banco-de-dados-por-serviço (database-per-service) implementado em todas as fronteiras lógicas.

## Otimizações para AWS Free Tier

Para manter os custos mínimos durante o desenvolvimento e apresentação:

| Decisão | Justificativa |
|---------|---------------|
| Sem NAT Gateway | ECS tasks em subnets públicas com `assign_public_ip = true` (~$32/mês economizados) |
| RDS Single-AZ | `db.t3.micro` Single-AZ é Free Tier por 12 meses |
| Sem Redis | Deduplicação in-memory (suficiente para MVP com 1 instância por serviço) |
| ECS 256 CPU / 512 MB | Configuração mínima do Fargate para reduzir custo |
| 1 réplica por serviço | Suficiente para demonstração |
| ECR Lifecycle Policy | Mantém apenas 3 imagens por repositório (500MB free) |
| CloudWatch 7 dias | Retenção mínima para ficar dentro dos 5GB free |
| Service Discovery (Cloud Map) | Comunicação inter-serviço sem ALB interno (custo ~$0) |

### Serviços que NÃO são Free Tier
- **ECS Fargate**: ~$50-70/mês (6 tasks com recursos mínimos)
- **ALB**: ~$16/mês + data transfer
- **Total**: ~$70-90/mês (proporcional ao tempo de uso)

## Arquitetura de Entrega (CI/CD Monorepo)

O processo de entrega contínua está dividido em dois workflows independentes no GitHub Actions:

1. **`services-ci-cd.yml`**
   - Acionado por alterações em caminhos de serviço (`api-gateway/**`, `upload-service/**`, `processing-service/**`, `ai-analysis-service/**`, `report-service/**`, `status-service/**`).
   - Detecta quais serviços mudaram com *path filtering*.
   - Executa build e testes apenas para os serviços alterados.
   - Gera imagem Docker e publica no **Amazon ECR** usando tag do commit (`github.sha`).
   - Atualiza a **Task Definition** e força novo deployment no **Amazon ECS (Fargate)** do serviço correspondente.

2. **`terraform-infra.yml`**
   - Acionado apenas quando há alteração em `terraform/**`.
   - Executa `terraform init`, `fmt`, `validate`, `plan`.
   - Executa `terraform apply` somente em `push` para `main`.

### Configuração de Ambientes no Pipeline

- `DEPLOY_ENV` é lido de **Repository Variables** e `AWS_REGION` de **Repository Secrets**.
- Ambos workflows também aceitam `workflow_dispatch` com `deploy_env` para execução manual.
- Credenciais AWS são obtidas por **OIDC** via `AWS_ROLE_ARN` (sem chaves estáticas no repositório).

## Diagrama Textual do Fluxo de CI/CD

```text
            +-----------------------------+
            | GitHub Repository (Monorepo)|
            +---------------+-------------+
                            |
                            v
                  +----------------------+
                  | GitHub Actions       |
                  | (Path-based trigger) |
                  +----------+-----------+
                             |
               +-------------+------------------+
               |                                |
               v                                v
   +---------------------------+      +--------------------------+
   | services-ci-cd.yml        |      | terraform-infra.yml      |
   | build/test por serviço    |      | plan/apply infra         |
   +------------+--------------+      +-------------+------------+
                |                                   |
                v                                   v
      +-----------------------+          +-----------------------+
      | Amazon ECR            |          | AWS Infra (Terraform) |
      | push imagem por svc   |          | VPC/RDS/ECS/ECR/...   |
      +-----------+-----------+          +-----------------------+
                  |
                  v
      +-----------------------+
      | Amazon ECS Fargate    |
      | update task + deploy  |
      +-----------------------+
```
