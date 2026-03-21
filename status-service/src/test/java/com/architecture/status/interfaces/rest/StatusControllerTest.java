package com.architecture.status.interfaces.rest;

import com.architecture.status.application.UpdateStatusUseCase;
import com.architecture.status.domain.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

    @Mock
    private UpdateStatusUseCase updateStatusUseCase;

    @InjectMocks
    private StatusController statusController;

    @Test
    void shouldReturn200WhenStatusExists() {
        Status status = Status.builder()
                .diagramId("diag-200")
                .state("PROCESSING")
                .lastUpdatedAt(LocalDateTime.now())
                .build();

        when(updateStatusUseCase.getStatus("diag-200")).thenReturn(status);

        ResponseEntity<StatusResponseDTO> response = statusController.getStatus("diag-200");

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("diag-200", response.getBody().getDiagramId());
        assertEquals("PROCESSING", response.getBody().getState());
    }

    @Test
    void shouldReturn404WhenStatusIsNotFound() {
        when(updateStatusUseCase.getStatus("diag-404"))
                .thenThrow(new RuntimeException("Status não encontrado para o diagrama: diag-404"));

        ResponseEntity<StatusResponseDTO> response = statusController.getStatus("diag-404");

        assertEquals(HttpStatusCode.valueOf(404), response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void shouldReturn500WhenUnexpectedErrorOccurs() {
        when(updateStatusUseCase.getStatus("diag-500"))
                .thenThrow(new RuntimeException("database timeout"));

        ResponseEntity<StatusResponseDTO> response = statusController.getStatus("diag-500");

        assertEquals(HttpStatusCode.valueOf(500), response.getStatusCode());
        assertNull(response.getBody());
    }
}
