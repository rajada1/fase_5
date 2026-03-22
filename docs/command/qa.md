Você é um Engenheiro de Software Sênior, Arquiteto de Nuvem, Especialista em IA e Especialista em DevOps.

# OBJETIVO
Realizar testes de código detalhados para o projeto de análise de diagramas arquiteturais conforme os requisitos abaixo:

# Requisitos funcionais 
# Funcionalidades obrigatórias: 
- Upload de diagrama (imagem ou PDF); 
- Criação de um processo de análise; 
- Consulta de status do processamento: 
     - Recebido; 
     - Em processamento; 
     - Analisado; 
     - Erro. 
- Geração de relatório contendo: 
- Componentes identificados; 
- Possíveis riscos arquiteturais; 
- Recomendações básicas.

# Requisitos técnicos

-Arquitetura baseada em microsserviços; 
-Comunicação via: 
-REST; 
-Ao menos um fluxo assíncrono (fila ou mensageria). 
- Aplicação de: 
- Clean Architecture ou Arquitetura Hexagonal; 
- Cada serviço deve: 
- Possuir responsabilidade clara; 
- Ter banco de dados próprio; 
- Ter testes automatizados. 

# Serviços mínimos sugeridos 
- API Gateway ou BFF; 
- Serviço de Upload e Orquestração; 
- Serviço de Processamento; 
- Serviço de Relatórios.

# A IA deve implementar ao menos uma das abordagens abaixo, considerando práticas de segurança, controle e avaliação do modelo: 
- Detecção de componentes arquiteturais em imagens; 
- Classificação de riscos arquiteturais a partir de regras + ML; 
- Uso de LLM para geração de relatório técnico estruturado, com implementação de guardrails para controle de entrada, saída e mitigação de alucinações; 
- Análise textual baseada em prompt engineering, incluindo validação de prompts, restrições de formato e avaliação da consistência das respostas. 

# Requisitos mínimos: 
- Pipeline claro de IA; 
- Justificativa da abordagem escolhida; 
- Demonstração prática da análise; 
- Discussão de limitações do modelo.

# A IA deve ser parte do fluxo do sistema, e não um script isolado. Deve ficar claro: 
- Como a IA é acionada; 
- Como o sistema trata falhas da IA; 
- Como o resultado da IA é persistido; 
- Como o relatório é gerado a partir da análise.

 # Infraestrutura e DevOps Obrigatório: 
- Docker; 
- Docker Compose ou Kubernetes; 
- Pipeline CI/CD contendo: 
- Build; 
- Testes; 
- Deploy (local ou cloud).

# Qualidade e observabilidade 
- Logs estruturados; 
- Tratamento de erros; 
- Testes unitários; 
- README explicativo.