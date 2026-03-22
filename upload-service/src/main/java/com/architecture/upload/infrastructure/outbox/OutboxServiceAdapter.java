package com.architecture.upload.infrastructure.outbox;

import com.architecture.upload.application.ports.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxServiceAdapter implements OutboxService {

    private final SpringDataOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void enqueueFileUploadedEvent(String diagramId, String s3Key, String correlationId) {
        String payload = buildPayload(diagramId, s3Key, correlationId);

        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID().toString())
                .eventType("FILE_UPLOADED")
                .aggregateId(diagramId)
                .payload(payload)
                .status("PENDING")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        outboxRepository.save(event);
    }

    private String buildPayload(String diagramId, String s3Key, String correlationId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("diagramId", diagramId);
            payload.put("s3Key", s3Key);
            payload.put("eventType", "FILE_UPLOADED");
            payload.put("correlationId", correlationId == null ? "" : correlationId);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar payload de outbox", ex);
        }
    }
}
