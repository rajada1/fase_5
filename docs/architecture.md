# Visão Geral da Arquitetura

Este diagrama representa o fluxo e a arquitetura para análise de diagramas de arquitetura de software usando IA. O sistema foi projetado em uma arquitetura de microsserviços, totalmente hospedado na AWS utilizando uma abordagem conteinerizada (ECS Fargate), comunicando-se via REST para tarefas síncronas e SQS/SNS para tarefas assíncronas.

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
