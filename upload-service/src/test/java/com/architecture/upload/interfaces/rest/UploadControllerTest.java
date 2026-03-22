package com.architecture.upload.interfaces.rest;

import com.architecture.upload.application.UploadUseCase;
import com.architecture.upload.domain.Diagram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Mock
    private UploadUseCase uploadUseCase;

    @InjectMocks
    private UploadController uploadController;

    @Test
    void shouldReturn400WhenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", new byte[0]);

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().getStatus());
        verifyNoInteractions(uploadUseCase);
    }

    @Test
    void shouldReturn400WhenFilenameIsMissing() {
        MockMultipartFile file = new MockMultipartFile("file", null, "image/png", "abc".getBytes());

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().getStatus());
        verifyNoInteractions(uploadUseCase);
    }

    @Test
    void shouldReturn400WhenFileIsTooLarge() {
        // 11MB file (exceeds 10MB limit)
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", largeContent);

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("excede o limite"));
        verifyNoInteractions(uploadUseCase);
    }

    @Test
    void shouldReturn400WhenFilenameContainsPathTraversal() {
        MockMultipartFile file = new MockMultipartFile("file", "../evil.png", "image/png", "abc".getBytes());

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().getStatus());
        verifyNoInteractions(uploadUseCase);
    }

    @Test
    void shouldReturn400WhenContentTypeIsNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "diagram.txt", "text/plain", "abc".getBytes());

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().getStatus());
        verifyNoInteractions(uploadUseCase);
    }

    @Test
    void shouldReturn400WhenMimeTypeSpoofingIsAttempted() {
        // Filename has .sh extension but content type is image/png (spoofing)
        MockMultipartFile file = new MockMultipartFile("file", "script.sh", "image/png", "echo 'hacked'".getBytes());

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        // Se a validação for robusta, deve falhar no spoofing.
        // O código atual do Controller apenas olha o Header `ContentType` e o nome puro.
        // Em um sistema seguro (QA Edge Case), devemos barrar extensões não correspondentes.
        // Simulando que vamos esperar 400. Ajuste o Controller se necessário para passar o teste.
        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().getStatus());
        verifyNoInteractions(uploadUseCase);
    }

    @Test
    void shouldReturn202WhenUploadSucceeds() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", "abc".getBytes());

        Diagram diagram = Diagram.builder().id("diag-202").build();
        when(uploadUseCase.uploadDiagram(eq("diagram.png"), any(), eq((long) file.getSize()), eq("image/png"), any()))
                .thenReturn(diagram);

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        assertEquals(HttpStatusCode.valueOf(202), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("diag-202", response.getBody().getDiagramId());
        assertEquals("RECEIVED", response.getBody().getStatus());
        assertTrue(response.getHeaders().containsKey(CORRELATION_ID_HEADER));
    }

    @Test
    void shouldReturn500WhenUseCaseReturnsNullId() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", "abc".getBytes());

        when(uploadUseCase.uploadDiagram(eq("diagram.png"), any(), eq((long) file.getSize()), eq("image/png"), any()))
                .thenReturn(Diagram.builder().id(null).build());

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        assertEquals(HttpStatusCode.valueOf(500), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ERROR", response.getBody().getStatus());
        assertTrue(response.getHeaders().containsKey(CORRELATION_ID_HEADER));
    }

    @Test
    void shouldReturn500WhenUseCaseThrowsException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", "abc".getBytes());

        when(uploadUseCase.uploadDiagram(eq("diagram.png"), any(), eq((long) file.getSize()), eq("image/png"), any()))
                .thenThrow(new RuntimeException("unexpected"));

        ResponseEntity<UploadResponseDTO> response = uploadController.uploadDiagram(file);

        assertEquals(HttpStatusCode.valueOf(500), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ERROR", response.getBody().getStatus());
        assertNull(response.getBody().getDiagramId());
        assertTrue(response.getHeaders().containsKey(CORRELATION_ID_HEADER));
    }
}
