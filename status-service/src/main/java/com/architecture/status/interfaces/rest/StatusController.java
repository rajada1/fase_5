package com.architecture.status.interfaces.rest;

import com.architecture.status.application.UpdateStatusUseCase;
import com.architecture.status.domain.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/status")
@RequiredArgsConstructor
@Slf4j
public class StatusController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final UpdateStatusUseCase updateStatusUseCase;

    @GetMapping("/{diagramId}")
    public ResponseEntity<StatusResponseDTO> getStatus(
            @PathVariable("diagramId") String diagramId,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationIdHeader) {
        String correlationId = resolveCorrelationId(correlationIdHeader);

        log.info("Consulta de status recebida. correlationId={} diagramId={}", correlationId, diagramId);

        Status status = updateStatusUseCase.getStatus(diagramId);

        StatusResponseDTO response = StatusResponseDTO.builder()
                .diagramId(status.getDiagramId())
                .state(status.getState())
                .lastUpdatedAt(status.getLastUpdatedAt())
                .build();

        log.info("Consulta de status concluída. correlationId={} diagramId={} state={}", correlationId,
                response.getDiagramId(), response.getState());

        return ResponseEntity.ok().header(CORRELATION_ID_HEADER, correlationId).body(response);
    }

    private String resolveCorrelationId(String correlationIdHeader) {
        if (correlationIdHeader == null || correlationIdHeader.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationIdHeader.trim();
    }
}
