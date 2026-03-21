package com.architecture.status.interfaces.rest;

import com.architecture.status.application.UpdateStatusUseCase;
import com.architecture.status.domain.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status")
@RequiredArgsConstructor
public class StatusController {

    private final UpdateStatusUseCase updateStatusUseCase;

    @GetMapping("/{diagramId}")
    public ResponseEntity<StatusResponseDTO> getStatus(@PathVariable String diagramId) {
        try {
            Status status = updateStatusUseCase.getStatus(diagramId);
            StatusResponseDTO response = StatusResponseDTO.builder()
                    .diagramId(status.getDiagramId())
                    .state(status.getState())
                    .lastUpdatedAt(status.getLastUpdatedAt())
                    .build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Status não encontrado")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }
}
