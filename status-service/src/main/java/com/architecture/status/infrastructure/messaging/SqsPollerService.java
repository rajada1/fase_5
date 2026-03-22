package com.architecture.status.infrastructure.messaging;

import com.architecture.status.application.UpdateStatusUseCase;
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
        pollQueue(uploadedQueueUrl, "Em processamento");
    }

    @Scheduled(fixedDelay = 2000)
    public void pollProcessedQueue() {
        pollQueue(processedQueueUrl, "Em processamento");
    }

    @Scheduled(fixedDelay = 2000)
    public void pollAnalysisQueue() {
        pollQueue(analysisQueueUrl, "Analisado");
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
            log.error("Erro ao consumir fila SQS. queueUrl={} error={}", queueUrl, e.getMessage());
        }
    }

    private void processMessage(Message message, String queueUrl, String newState) {
        try {
            JsonNode bodyNode;
            try {
                bodyNode = objectMapper.readTree(message.body());

                if (bodyNode.has("Message")) {
                    bodyNode = objectMapper.readTree(bodyNode.get("Message").asText());
                }
            } catch (JsonProcessingException jsonError) {
                log.warn("Mensagem inválida recebida da fila. queueUrl={} reason=malformed_payload action=discard",
                        queueUrl);
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
                return;
            }

            if (!bodyNode.hasNonNull("diagramId") || bodyNode.get("diagramId").asText().isBlank()) {
                log.warn("Mensagem inválida recebida da fila. queueUrl={} reason=missing_diagramId action=discard",
                        queueUrl);
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
                return;
            }

            String diagramId = bodyNode.get("diagramId").asText();
            String eventType = bodyNode.hasNonNull("eventType") ? bodyNode.get("eventType").asText("") : "";
            String correlationId = bodyNode.hasNonNull("correlationId") ? bodyNode.get("correlationId").asText("")
                    : "";
            String resolvedState = resolveTargetState(bodyNode, newState);

            log.info(
                    "Atualizando status do diagrama. diagramId={} eventType={} correlationId={} targetState={} queueUrl={}",
                    diagramId,
                    eventType,
                    correlationId,
                    resolvedState,
                    queueUrl);

            updateStatusUseCase.updateStatus(diagramId, resolvedState);

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());

        } catch (Exception e) {
            log.error("Falha ao processar mensagem da fila. queueUrl={} error={}", queueUrl, e.getMessage());
        }
    }

    private String resolveTargetState(JsonNode bodyNode, String defaultState) {
        String eventType = bodyNode.hasNonNull("eventType") ? bodyNode.get("eventType").asText("") : "";
        if (eventType != null && eventType.toUpperCase().endsWith("_FAILED")) {
            return "Erro";
        }
        return defaultState;
    }
}
