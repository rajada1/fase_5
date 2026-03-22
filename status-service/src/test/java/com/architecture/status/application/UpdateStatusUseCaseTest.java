package com.architecture.status.application;

import com.architecture.status.application.ports.StatusRepository;
import com.architecture.status.domain.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateStatusUseCaseTest {

    @Mock
    private StatusRepository statusRepository;

    @InjectMocks
    private UpdateStatusUseCase updateStatusUseCase;

    private static final String DIAGRAM_ID = "diag-123";

    @Test
    void shouldSuccessfullyTransitionFromReceivedToProcessing() {
        Status existingStatus = Status.builder().diagramId(DIAGRAM_ID).state("RECEIVED").build();
        when(statusRepository.findByDiagramIdForUpdate(DIAGRAM_ID)).thenReturn(Optional.of(existingStatus));

        updateStatusUseCase.updateStatus(DIAGRAM_ID, "PROCESSING");

        verify(statusRepository)
                .save(argThat(status -> "PROCESSING".equals(status.getState()) && status.getLastUpdatedAt() != null));
    }

    @Test
    void shouldThrowExceptionWhenTransitioningBackwards() {
        Status existingStatus = Status.builder().diagramId(DIAGRAM_ID).state("ANALYZED").build();
        when(statusRepository.findByDiagramIdForUpdate(DIAGRAM_ID)).thenReturn(Optional.of(existingStatus));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> updateStatusUseCase.updateStatus(DIAGRAM_ID, "PROCESSING"));

        assertTrue(exception.getMessage().contains("Transição de status inválida"));
        verify(statusRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenStateIsInvalid() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> updateStatusUseCase.updateStatus(DIAGRAM_ID, "LIXO_INJETADO"));

        assertTrue(exception.getMessage().contains("Estado de status inválido"));
        verify(statusRepository, never()).save(any());
    }

    @Test
    void shouldCreateNewStateIfNotFound() {
        when(statusRepository.findByDiagramIdForUpdate(DIAGRAM_ID)).thenReturn(Optional.empty());

        updateStatusUseCase.updateStatus(DIAGRAM_ID, "RECEIVED");

        verify(statusRepository).save(
                argThat(status -> "RECEIVED".equals(status.getState()) && status.getDiagramId().equals(DIAGRAM_ID)));
    }

    @Test
    void shouldIgnoreDuplicateStateTransition() {
        Status existingStatus = Status.builder().diagramId(DIAGRAM_ID).state("PROCESSING").build();
        when(statusRepository.findByDiagramIdForUpdate(DIAGRAM_ID)).thenReturn(Optional.of(existingStatus));

        updateStatusUseCase.updateStatus(DIAGRAM_ID, "PROCESSING");

        verify(statusRepository, never()).save(any());
    }
}
