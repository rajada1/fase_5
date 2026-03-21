package com.architecture.status.application;

import com.architecture.status.application.ports.StatusRepository;
import com.architecture.status.domain.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UpdateStatusUseCase {

    private final StatusRepository statusRepository;

    @Transactional
    public void updateStatus(String diagramId, String state) {
        if (!isKnownState(state)) {
            throw new IllegalArgumentException("Estado de status inválido: " + state);
        }

        Status status = statusRepository.findByDiagramId(diagramId).orElseGet(() -> Status.builder()
                .diagramId(diagramId)
                .build());

        // Business Rule: Validate state transition
        if (status.getState() != null) {
            int currentOrder = getStatusOrder(status.getState());
            int newOrder = getStatusOrder(state);

            if (newOrder < currentOrder) {
                throw new IllegalStateException(
                        String.format("Transição de status inválida. Não é possível retornar de %s para %s.",
                                status.getState(), state));
            }
        }

        status.setState(state);
        status.setLastUpdatedAt(LocalDateTime.now());
        statusRepository.save(status);
    }

    @Transactional(readOnly = true)
    public Status getStatus(String diagramId) {
        return statusRepository.findByDiagramId(diagramId)
                .orElseThrow(() -> new RuntimeException("Status não encontrado para o diagrama: " + diagramId));
    }

    private int getStatusOrder(String state) {
        if (state == null)
            return -1;
        switch (state.toUpperCase()) {
            case "RECEIVED":
                return 0;
            case "PROCESSING":
                return 1;
            case "ANALYZING":
                return 2;
            case "COMPLETED":
                return 3;
            case "FAILED":
                return 4;
            default:
                throw new IllegalArgumentException("Estado de status inválido: " + state);
        }
    }

    private boolean isKnownState(String state) {
        if (state == null)
            return false;
        switch (state.toUpperCase()) {
            case "RECEIVED":
            case "PROCESSING":
            case "ANALYZING":
            case "COMPLETED":
            case "FAILED":
                return true;
            default:
                return false;
        }
    }
}
