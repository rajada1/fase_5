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
        Status existingStatus = Status.builder().diagramId(DIAGRAM_ID).state("Recebido").build();
        when(statusRepository.findByDiagramIdForUpdate(DIAGRAM_ID)).thenReturn(Optional.of(existingStatus));

        updateStatusUseCase.updateStatus(DIAGRAM_ID, "Em processamento");

        verify(statusRepository)
                .save(argThat(
                        status -> "Em processamento".equals(status.getState()) && status.getLastUpdatedAt() != null));
    }

    @Test
    void shouldThrowExceptionWhenTransitioningBackwards() {
        Status existingStatus = Status.builder().diagramId(DIAGRAM_ID).state("Analisado").build();
        when(statusRepository.findByDiagramIdForUpdate(DIAGRAM_ID)).thenReturn(Optional.of(existingStatus));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> updateStatusUseCase.updateStatus(DIAGRAM_ID, "Em processamento"));

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

        updateStatusUseCase.updateStatus(DIAGRAM_ID, "Recebido");

        verify(statusRepository).save(
                argThat(status -> "Recebido".equals(status.getState()) && status.getDiagramId().equals(DIAGRAM_ID)));
    }

    @Test
    void shouldIgnoreDuplicateStateTransition() {
        Status existingStatus = Status.builder().diagramId(DIAGRAM_ID).state("Em processamento").build();
        when(statusRepository.findByDiagramIdForUpdate(DIAGRAM_ID)).thenReturn(Optional.of(existingStatus));

        updateStatusUseCase.updateStatus(DIAGRAM_ID, "Em processamento");

        verify(statusRepository, never()).save(any());
    }
}
