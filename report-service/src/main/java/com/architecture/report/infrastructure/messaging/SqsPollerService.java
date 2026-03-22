package com.architecture.report.infrastructure.messaging;

import com.architecture.report.application.GenerateReportUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqsPollerService {

    private final SqsClient sqsClient;
    private final GenerateReportUseCase generateReportUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.sqs.analysis-completed-queue-url}")
    private String queueUrl;

    @Scheduled(fixedDelay = 5000)
    public void pollMessages() {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(5)
                    .waitTimeSeconds(5)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            for (Message message : messages) {
                processMessage(message);
            }
        } catch (Exception e) {
            log.error("Erro ao consumir fila SQS. queueUrl={} error={}", queueUrl, e.getMessage());
        }
    }

    private void processMessage(Message message) {
        try {
            JsonNode bodyNode;
            try {
                bodyNode = objectMapper.readTree(message.body());

                if (bodyNode.has("Message")) {
                    bodyNode = objectMapper.readTree(bodyNode.get("Message").asText());
                }
            } catch (JsonProcessingException jsonError) {
                log.warn(
                        "Mensagem inválida recebida na fila de relatório. queueUrl={} reason=malformed_payload action=discard",
                        queueUrl);
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
                return;
            }

            if (!bodyNode.hasNonNull("diagramId") || bodyNode.get("diagramId").asText().isBlank()
                    || !bodyNode.has("analysis")) {
                String diagramId = bodyNode.hasNonNull("diagramId") ? bodyNode.get("diagramId").asText("") : "";
                String eventType = bodyNode.hasNonNull("eventType") ? bodyNode.get("eventType").asText("") : "";
                String correlationId = bodyNode.hasNonNull("correlationId") ? bodyNode.get("correlationId").asText("")
                        : "";
                log.warn(
                        "Mensagem inválida recebida na fila de relatório. diagramId={} eventType={} correlationId={} queueUrl={} reason=missing_diagramId_or_analysis action=discard",
                        diagramId,
                        eventType,
                        correlationId,
                        queueUrl);
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
                return;
            }

            String diagramId = bodyNode.get("diagramId").asText();
            String analysisData = bodyNode.get("analysis").toString();
            String eventType = bodyNode.hasNonNull("eventType") ? bodyNode.get("eventType").asText("") : "";
            String correlationId = bodyNode.hasNonNull("correlationId") ? bodyNode.get("correlationId").asText("") : "";

            log.info(
                    "Recebida análise para geração de relatório. diagramId={} eventType={} correlationId={} queueUrl={}",
                    diagramId, eventType, correlationId, queueUrl);

            generateReportUseCase.generateAndSaveReport(diagramId, analysisData);

            // Delete message on success
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());

        } catch (Exception e) {
            log.error("Falha ao processar mensagem de relatório. queueUrl={} error={}", queueUrl, e.getMessage());
        }
    }
}
