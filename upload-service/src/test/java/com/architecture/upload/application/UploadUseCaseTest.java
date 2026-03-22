package com.architecture.upload.application;

import com.architecture.upload.application.ports.DiagramRepository;
import com.architecture.upload.application.ports.OutboxService;
import com.architecture.upload.application.ports.StorageService;
import com.architecture.upload.domain.Diagram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadUseCaseTest {

    @Mock
    private StorageService storageService;

    @Mock
    private DiagramRepository diagramRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private UploadUseCase uploadUseCase;

    @Test
    void shouldUploadDiagramAndInitializeWithReceivedStatus() {
        // Given
        String fileName = "architecture.png";
        InputStream mockStream = new ByteArrayInputStream("dummy".getBytes());
        long fileSize = 5L;
        String contentType = "image/png";

        // When
        Diagram result = uploadUseCase.uploadDiagram(fileName, mockStream, fileSize, contentType, "corr-123");

        // Then expected business rules:
        assertNotNull(result);
        assertNotNull(result.getId());

        // 1. Validate Initial State is Recebido
        assertEquals("Recebido", result.getStatus(), "O diagrama deve obrigatoriamente iniciar com status Recebido");
        assertEquals(fileName, result.getOriginalFileName());

        // 2. Validate Extension extraction
        assertTrue(result.getS3Key().endsWith(".png"), "A chave do S3 deve manter a extensão original do arquivo");

        // 3. Validate orchestrations (storage -> db -> outbox)
        verify(storageService).uploadFile(eq(result.getS3Key()), eq(mockStream), eq(fileSize), eq(contentType));

        verify(diagramRepository)
                .save(argThat(d -> d.getId().equals(result.getId()) && "Recebido".equals(d.getStatus())));

        // 4. Validate Event formulation
        verify(outboxService).enqueueFileUploadedEvent(result.getId(), result.getS3Key(), "corr-123");
    }
}
