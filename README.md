# Analisador de Arquitetura de Software com IA

Este projeto é um sistema backend distribuído construído com uma arquitetura de microsserviços. Ele recebe diagramas de arquitetura de software (imagens ou PDFs), processa os arquivos usando OCR (simulado neste contexto), e utiliza Inteligência Artificial (banco em LLMs) para identificar componentes, verificar pontos únicos de falha, apontar riscos na infraestrutura e sugerir recomendações de melhoria. 

Toda a arquitetura é "cloud-native" e foi projetada para rodar integralmente dentro da AWS (Amazon Web Services), mas também dispõe de infraestrutura configurada com LocalStack para execução local.

## 🚀 Tecnologias e Padrões Aplicados

- **Microsserviços:** 6 serviços independentes, arquitetados sob os preceitos rigorosos da *Clean Architecture (Hexagonal)*.
- **Ecossistema Java:** Spring Boot 3, Spring Cloud Gateway, Spring Data JPA, AWS SDK v2, Maven.
- **Ecossistema Python:** FastAPI, Pydantic, Boto3, OpenAI API.
- **Banco de Dados:** PostgreSQL (Padrão: 1 Database por Microsserviço).
- **Processamento Assíncrono:** Mensageria via Amazon SQS e Amazon SNS.
- **Armazenamento:** Amazon S3.
- **Infraestrutura como Código (IaC):** Terraform.
- **Conteinerização e Testes Locais:** Docker e Docker Compose (com Localstack).

## 📁 Estrutura do Monorepo

`/fase_5`
* `api-gateway/` : Roteador central e controle de acessos (Spring Cloud).
* `upload-service/` : Recebe a imagem, insere no S3 e avisa via SNS (Spring Boot).
* `processing-service/` : Consome a fila SQS, extrai dados simulando OCR (Python/FastAPI).
* `ai-analysis-service/` : Analisa os componentes via IA e reporta falhas e riscos (Python/FastAPI).
* `report-service/` : Gera o JSON de diagnóstico final em um banco próprio (Spring Boot).
* `status-service/` : Tarefa cron que escuta as filas para rastrear e persistir o status global de todo o pipeline (Spring Boot).
* `terraform/` : IaC declarativo em HCL para produção (AWS RDS, ECR, SQS, SNS, S3).
* `docs/` : Detalhamento avançado do fluxo, arquitetura textual e arquivos complementares.
* `init-aws.sh` : Script de configuração automática de mensageria (queues/topics/bucket) na inicialização do LocalStack.
* `init-dbs.sql` : Script de configuração automática dos três databases lógicos no Postgres via docker.

---

## ⚙️ Passo a Passo para Execução Local

Você precisará de ter o [Docker](https://www.docker.com/) e o Maven/Python instalados em sua máquina.

### Passo 1: Iniciar os Simuladores da AWS e Bancos de Dados
Na raiz do projeto (`/fase_5`), suba a infraestrutura base:
```bash
docker-compose up -d
```
O `docker-compose.yml` criará um contêiner Postgres (na porta `5432` que já recriará os bancos isolados via `init-dbs.sql`) e um contêiner LocalStack (na porta `4566`, que por padrão recriará automaticamente as filas, tópicos e buckets via `init-aws.sh`).

Aguarde alguns instantes até que estes serviços inicializem. Você pode visualizar os logs no Docker Desktop.

### Passo 2: Iniciar os Microsserviços Individualmente
Para um teste de desenvolvimento rápido, você pode rodar (na IDE ou no terminal) cada aplicação separadamente:

Serviços **Java (Spring Boot)** (em terminais separados prestando atenção nas suas respectivas pastas):
```bash
cd api-gateway
mvn spring-boot:run
```
*(Repita para upload-service, report-service e status-service)*

Serviços **Python (FastAPI)** (em terminais separados nas suas respectivas pastas):
```bash
cd processing-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8082
```
*(Repita para ai-analysis-service na porta 8083)*

**Portas Esperadas para cada serviço:**
- API Gateway: `8080`
- Upload Service: `8081`
- Processing Service: `8082`
- AI Analysis Service: `8083`
- Report Service: `8084`
- Status Service: `8085`

---

## 🧪 Testando o Fluxo Ponta a Ponta

Todo o sistema é orientado a eventos. Uma única chamada rest interage sequencialmente por debaixo dos panos com instâncias e roteadores de filas do Amazon SQS e SNS até finalizar.

**1. Faça o Upload de um Arquivo de Diagrama:**
```bash
curl -X POST http://localhost:8080/api/v1/upload \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ=" \
  -F "file=@/SEU/CAMINHO/LOCAL/PARA/O/diagrama.png"
```
Você receberá um JSON contento a chave `diagramId` na resposta (ex: `e7b9...`). Guarde este ID.

**2. Acompanhe a Esteira de Status:**

Como o processamento pode demorar, o client realiza _Long-Polling_ localizando o último status no Status Service utilizando o `diagramId` da etapa anterior:
```bash
curl -X GET http://localhost:8080/api/v1/status/{diagramId} \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```
O Estado retornado transitará na respectiva ordem contínua: `RECEIVED` → `PROCESSING` → `ANALYZING` → `COMPLETED`.

**3. Buscar o Diagnóstico de IA Final:**

Quando o fluxo apontar como status definitivo o enum de estado `COMPLETED`, sua resposta final com riscos infraestruturais detalhados pela IA poderá ser buscada aqui:
```bash
curl -X GET http://localhost:8080/api/v1/reports/{diagramId} \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

## ☁️ Implantação em Produção (AWS)
O projeto contém a pasta `/terraform`.
Uma vez ajustadas as credenciais no seu AWS CLI, rode `terraform init` e `terraform apply` contendo a respectiva conta autenticada para instanciar os repositórios reais e serviços faturáveis no provedor da Amazon. As configurações padrões vão alocar 6 Repositórios ECR, Instâncias t3.micro do Amazon RDS e as referências completas para filas de mensageria SNS/SQS.
