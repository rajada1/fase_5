package com.architecture.report.infrastructure.messaging;

import com.architecture.report.application.GenerateReportUseCase;
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
            log.error("Error polling SQS queue: {}", e.getMessage());
        }
    }

    private void processMessage(Message message) {
        try {
            JsonNode bodyNode = objectMapper.readTree(message.body());

            // Handle SNS wrapper if present
            if (bodyNode.has("Message")) {
                bodyNode = objectMapper.readTree(bodyNode.get("Message").asText());
            }

            if (!bodyNode.hasNonNull("diagramId") || bodyNode.get("diagramId").asText().isBlank()
                    || !bodyNode.has("analysis")) {
                log.warn(
                        "Mensagem inválida recebida na fila de relatório (sem diagramId/analysis). A mensagem será descartada.");
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
                return;
            }

            String diagramId = bodyNode.get("diagramId").asText();
            String analysisData = bodyNode.get("analysis").toString();

            log.info("Received analysis for diagram: {}", diagramId);

            generateReportUseCase.generateAndSaveReport(diagramId, analysisData);

            // Delete message on success
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());

        } catch (Exception e) {
            log.error("Failed to process message: {}", e.getMessage());
        }
    }
}
