package com.architecture.upload.infrastructure.outbox;

import com.architecture.upload.application.ports.MessagingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_DEAD = "DEAD";

    private final SpringDataOutboxEventRepository outboxRepository;
    private final MessagingService messagingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${outbox.publisher.max-retries:5}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pending = outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(STATUS_PENDING);
        List<OutboxEventEntity> failed = outboxRepository
                .findTop20ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(STATUS_FAILED, maxRetries);

        List<OutboxEventEntity> eventsToProcess = new ArrayList<>(pending);
        eventsToProcess.addAll(failed);

        for (OutboxEventEntity event : eventsToProcess) {
            publishSingleEvent(event);
        }
    }

    private void publishSingleEvent(OutboxEventEntity event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            String diagramId = payload.path("diagramId").asText();
            String s3Key = payload.path("s3Key").asText();

            if (diagramId.isBlank() || s3Key.isBlank()) {
                throw new IllegalArgumentException("Payload de outbox inválido: diagramId/s3Key ausente");
            }

            messagingService.publishFileUploadedEvent(diagramId, s3Key);
            event.setStatus(STATUS_SENT);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);

            outboxRepository.save(event);
        } catch (Exception e) {
            int nextRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(nextRetryCount);
            event.setLastError(truncate(e.getMessage()));
            event.setStatus(nextRetryCount >= maxRetries ? STATUS_DEAD : STATUS_FAILED);

            outboxRepository.save(event);
            log.error("Falha ao publicar evento de outbox id={} tentativa={}", event.getId(), nextRetryCount, e);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
