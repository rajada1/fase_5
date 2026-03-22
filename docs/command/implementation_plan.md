# QA Testing Implementation Plan

O objetivo desta tarefa é implementar testes de unidade e integração focados em **Edge Cases e Cenários de Falha**, garantindo a robustez dos microsserviços do projeto de análise de diagramas arquiteturais. 

## Proposed Changes

---

### Serviço de Upload (Java)
Iremos forçar erros no upload de aquivos, validando os limites de tamanho (gigantesco ou nulo), tipos de extensão maliciosa (spoofing) e segurança no nome do arquivo.

#### [MODIFY] [UploadControllerTest.java](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/upload-service/src/test/java/com/architecture/upload/interfaces/rest/UploadControllerTest.java)
- Adicionar testes de spoofing de arquivos ([.sh](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/init-aws.sh) renomeado para `.pdf`).
- Adicionar testes de arquivo com `0 bytes`.
- Simular erro de TimeOut e indisponibilidade na fila SQS/mensageria usando mocks.

---

### Serviço de Processamento (Python/Pytest)
Foco em resiliência (Retry, Exponential Backoff, DLQ) e cenários anômalos de extração.

#### [MODIFY] [test_ocr_service.py](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/processing-service/tests/test_ocr_service.py)
- Mockar a API de OCR para retornar erro 503 HTTP (Too Many Requests / Indisponível) testando o fallback.
- Testar comportamento com imagem completamente inelegível/preta, para garantir que o sistema rejeita falhas graciosamente.

#### [MODIFY] [test_sqs_poller_flow.py](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/processing-service/tests/test_sqs_poller_flow.py)
- Injetar exceção (Poison Pill message) e verificar se o sistema direciona a mensagem para o log de falhas (DLQ mock/log).

---

### Serviço de Análise de IA (Python/Pytest)
Como este serviço se comunica com a IA (LLM), o tratamento de erros é crítico.

#### [MODIFY] [test_llm_service.py](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/ai-analysis-service/tests/test_llm_service.py)
- Testar *Prompt Injection* (simulando guardrails).
- Testar falhas de parsing JSON (quando o LLM retorna texto em prosa ao invés de um JSON válido).
- Testar resposta de limite de Tokens (API retornando _context_length_exceeded_).

---

### Serviço de Status (Java)

#### [MODIFY] [UpdateStatusUseCaseTest.java](file:///c:/Users/Meu%20Computador/OneDrive/%C3%81rea%20de%20Trabalho/FIAP/projeto/fase_5/status-service/src/test/java/com/architecture/status/application/UpdateStatusUseCaseTest.java)
- Tentar transicionar um Status inválido ou consultar Status de UUIDs malformados.
- Testar concorrência na gravação do mesmo evento (idempotência).


## Verification Plan

### Automated Tests
Irei rodar os seguintes comandos em cada diretório para validar as rotinas implementadas:

**Para Serviços Java (Upload, Status, Report):**
```bash
mvn test
```

**Para Serviços Python (Processing, AI Analysis):**
```bash
pytest tests/
```

### Manual Verification
Nenhum teste manual será necessário. Todo o plano de testes foca no nível de testes automatizados de injeção de falhas (mockadas) e tratamento de erros do próprio código.
