You are a Senior Software Engineer, Cloud Architect, and AI Specialist.

Your task is to generate a complete microservices-based backend system for analyzing software architecture diagrams using AI, fully designed to run on AWS.

# SYSTEM GOAL
Build a cloud-native system that:
* Receives architecture diagrams (image or PDF)
* Processes and stores the diagram securely
* Uses AI to analyze the architecture
* Generates a structured technical report
* Provides asynchronous processing status

# ARCHITECTURE CONSTRAINTS & PATTERNS
* Microservices architecture
* Clean Architecture (or Hexagonal Architecture) strictly applied
* Database per service pattern
* Communication patterns:
  * REST API for synchronous calls
  * AWS SQS/SNS for asynchronous event-driven communication
* Containerized environment ready for AWS ECS (Fargate)

# REQUIRED MICROSERVICES & AWS INTEGRATION

1. API Gateway (AWS API Gateway or Spring Cloud Gateway)
* Single entry point for all client requests
* Routes requests to internal services
* Handles basic authentication/authorization mock

2. Upload Service (Java + Spring Boot)
* Accepts file uploads (image/pdf)
* Uploads raw files to Amazon S3
* Stores metadata in PostgreSQL (Amazon RDS)
* Publishes `FileUploadedEvent` to SNS/SQS
* Creates initial analysis job

3. Processing Service (Python + FastAPI)
* Triggered by SQS message
* Downloads file from S3
* Converts PDF to image
* Applies OCR (mock or real) to extract text and components
* Publishes `DiagramProcessedEvent` with extracted payload

4. AI Analysis Service (Python + FastAPI)
* Triggered by SQS message
* Analyzes extracted data using LLM (Simulated or OpenAI integration)
* Identifies architecture components, single points of failure, missing API gateways, and shared databases
* Generates structured JSON analysis
* Publishes `AnalysisCompletedEvent`

5. Report Service (Java + Spring Boot)
* Consumes AI output events
* Generates final formatted report
* Persists report data in PostgreSQL (Amazon RDS)
* Provides GET endpoint for report retrieval

6. Status Service (Java + Spring Boot)
* Consumes events from all services to track job status
* Status states: RECEIVED -> PROCESSING -> ANALYZED -> ERROR
* Backed by its own PostgreSQL database

# TECHNOLOGY STACK
* Backend: Java (Spring Boot) and Python (FastAPI)
* Databases: PostgreSQL (Amazon RDS)
* Storage: Amazon S3
* Messaging: Amazon SQS / SNS
* Containerization: Docker
* Infrastructure as Code: Terraform

# PROJECT STRUCTURE (MONOREPO)
Generate a monorepo structured exactly like this:
/project-root
  /api-gateway
  /upload-service
  /processing-service
  /ai-analysis-service
  /report-service
  /status-service
  /terraform (AWS IaC)
  docker-compose.yml (for local development only)

# REQUIREMENTS FOR EACH MICROSERVICE
Each service must explicitly include:
* Clean Architecture layers: domain, application, infrastructure, interfaces (controllers)
* REST endpoints (Controllers/Routers)
* DTOs for request/response
* Basic Unit Tests
* Global Exception Handling
* Structured Logging

# AI OUTPUT REQUIREMENTS
The AI Analysis Service must output this exact JSON structure:
{
  "components": ["list of identified nodes/services"],
  "risks": [
    {
      "type": "Single Point of Failure | Shared DB | Security | Other",
      "description": "Risk details"
    }
  ],
  "recommendations": ["Actionable improvement items"]
}

# EXECUTION STRATEGY
Execute and generate the code strictly step-by-step. Do not skip any steps.
1. Architecture overview & AWS infrastructure diagram (Text description)
2. Folder structure generation
3. Terraform code for AWS base infra (S3, SQS, SNS, RDS, ECR)
4. API Gateway implementation
5. Upload Service implementation
6. Processing Service implementation
7. AI Analysis Service implementation
8. Report Service implementation
9. Status Service implementation
10. Local docker-compose.yml (simulating AWS services with LocalStack if possible, or standard containers)
11. Example usage (cURL commands for testing)