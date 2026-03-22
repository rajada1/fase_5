# Relatório de Testes QA: Edge Cases e Tolerância a Falhas

Como Especialista de QA, os testes implementados nesta sessão focaram ativamente em "quebrar" a aplicação e garantir resiliência, simulando cenários extremos nos 5 microsserviços do sistema de análise arquitetural.

## 1. Upload Service (Java)
**O que foi testado**: Falhas e invasões de camada perimetral.
- **MIME Type Spoofing**: Simulação de um arquivo malicioso renomeado (`script.sh` para `.pdf`) mas mantendo um header adulterado. O [UploadController](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/upload-service/src/main/java/com/architecture/upload/interfaces/rest/UploadController.java#17-179) foi ajustado para vetar inconsistências de extensão.
- **Zero-Byte Uploads**: Rejeição correta de arquivos nulos, que de outra forma corromperiam o *Processing Service*.
- *Validação:* `mvn test` passou em todos os testes do [UploadControllerTest.java](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/upload-service/src/test/java/com/architecture/upload/interfaces/rest/UploadControllerTest.java).

## 2. Status Service (Java)
**O que foi testado**: Idempotência e injeção de estado.
- **Injeção de Status Inválido**: Validação do UseCase disparando `IllegalArgumentException` ao invés de salvar strings maliciosas. A mensagem apropriada ("Estado de status inválido") foi firmemente atrelada ao teste.
- **Transição Cíclica (Error/Backwards)**: Tentar retroceder o status de forma maliciosa via API é validado e impedido, conservando a máquina de estados.
- *Validação:* `mvn test` passou e a cobertura restrita de *Unnecessary Stubbing* no Mockito foi corrigida.

## 3. Processing Service (Python/Pytest)
**O que foi testado**: Limites do processamento assíncrono e falibilidade da Infra AWS.
- **Poison Pill na Fila (SQS)**: Uma mensagem falha criticamente dentro do worker, estourando uma exceção de Runtime. O poller agora captura graciosamente o erro, publica uma notificação de "Failed Event" de volta para o barramento, e apaga a mensagem venenosa para evitar loop infinito na fila.
- **Cenário "Imagem Preta" / OCR Ilegível**: Quando o AWS Textract falha em detectar blocos e o PyTesseract extrai `""`, o serviço levanta um `RuntimeError("Nenhum texto detectado")`, permitindo que o orquestrador classifique como erro analítico humano.
- *Validação:* `pytest tests/` passou.

## 4. AI Analysis Service (Python/Pytest)
**O que foi testado**: Limites do LLM e alucinações.
- **Respostas Categóricas Inválidas (Falha de Parseamento)**: Simulado o cenário em que a Gemini descarta o formato JSON obrigatório e responde num texto corrido. O sistema engole a falha de schema (`Expecting value`) levantando um bloqueio _Não-Retryable_ explícito, impedindo retentativas inúteis tarifadas.
- **Limite de Contexto/Capacidade**: O LLM retorna erro de capacidade/contexto da Gemini. O sistema mapeia o erro como transitório quando aplicável e aciona a camada de DLQ/Retry da aplicação.
- *Validação:* `pytest tests/` passou.

## 5. Report Service (Java)
**O que foi testado**: Concorrência de banco de dados e resiliência.
- **Exceção de Violação de Integridade**: Ocorrem *race conditions* de concorrência onde dois workers tentam salvar um report ao mesmo tempo ([DataIntegrityViolationException](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/report-service/src/test/java/com/architecture/report/application/GenerateReportUseCaseTest.java#52-80)). Implementado um fallback no [GenerateReportUseCase](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/report-service/src/main/java/com/architecture/report/application/GenerateReportUseCase.java#13-47) que resgata a exception do repository e faz um Update subsequente. 
- *Validação:* `mvn test` finalizado com sucesso abrangendo a nova estrutura de falha.

---

> [!TIP]
> A implementação de abordagens estritas (onde todo _Exception_ é traduzido para um `Status=Erro` do diagrama) impede falhas silenciosas na aplicação. Satisfeitos os requisitos da análise!
