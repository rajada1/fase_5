package com.architecture.status.infrastructure.messaging;

import com.architecture.status.application.UpdateStatusUseCase;
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
    private final UpdateStatusUseCase updateStatusUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.sqs.file-uploaded-queue-url}")
    private String uploadedQueueUrl;

    @Value("${aws.sqs.diagram-processed-queue-url}")
    private String processedQueueUrl;

    @Value("${aws.sqs.analysis-completed-queue-url}")
    private String analysisQueueUrl;

    @Scheduled(fixedDelay = 2000)
    public void pollUploadedQueue() {
        pollQueue(uploadedQueueUrl, "PROCESSING");
    }

    @Scheduled(fixedDelay = 2000)
    public void pollProcessedQueue() {
        pollQueue(processedQueueUrl, "ANALYZING");
    }

    @Scheduled(fixedDelay = 2000)
    public void pollAnalysisQueue() {
        pollQueue(analysisQueueUrl, "COMPLETED");
    }

    private void pollQueue(String queueUrl, String newState) {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(5)
                    .waitTimeSeconds(2)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            for (Message message : messages) {
                processMessage(message, queueUrl, newState);
            }
        } catch (Exception e) {
            log.error("Error polling SQS queue {}: {}", queueUrl, e.getMessage());
        }
    }

    private void processMessage(Message message, String queueUrl, String newState) {
        try {
            JsonNode bodyNode = objectMapper.readTree(message.body());

            if (bodyNode.has("Message")) {
                bodyNode = objectMapper.readTree(bodyNode.get("Message").asText());
            }

            if (!bodyNode.hasNonNull("diagramId") || bodyNode.get("diagramId").asText().isBlank()) {
                log.warn("Mensagem inválida recebida da fila {} sem diagramId. A mensagem será descartada.", queueUrl);
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
                return;
            }

            String diagramId = bodyNode.get("diagramId").asText();

            log.info("Updating status for diagram: {} to {}", diagramId, newState);

            updateStatusUseCase.updateStatus(diagramId, newState);

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());

        } catch (Exception e) {
            log.error("Failed to process message from {}: {}", queueUrl, e.getMessage());
        }
    }
}
