package com.architecture.report.interfaces.rest;

import com.architecture.report.application.GenerateReportUseCase;
import com.architecture.report.domain.Report;
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
class ReportControllerTest {

    @Mock
    private GenerateReportUseCase generateReportUseCase;

    @InjectMocks
    private ReportController reportController;

    @Test
    void shouldReturn200WhenReportExists() {
        Report report = Report.builder()
                .id("rpt-200")
                .diagramId("diag-200")
                .content("{\"risks\":[]}")
                .generatedAt(LocalDateTime.now())
                .build();

        when(generateReportUseCase.getReportByDiagramId("diag-200")).thenReturn(report);

        ResponseEntity<ReportResponseDTO> response = reportController.getReport("diag-200");

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("rpt-200", response.getBody().getId());
        assertEquals("diag-200", response.getBody().getDiagramId());
    }

    @Test
    void shouldReturn404WhenReportIsNotFound() {
        when(generateReportUseCase.getReportByDiagramId("diag-404"))
                .thenThrow(new RuntimeException("Relatório não encontrado para o diagrama: diag-404"));

        ResponseEntity<ReportResponseDTO> response = reportController.getReport("diag-404");

        assertEquals(HttpStatusCode.valueOf(404), response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void shouldReturn500WhenUnexpectedErrorOccurs() {
        when(generateReportUseCase.getReportByDiagramId("diag-500"))
                .thenThrow(new RuntimeException("database timeout"));

        ResponseEntity<ReportResponseDTO> response = reportController.getReport("diag-500");

        assertEquals(HttpStatusCode.valueOf(500), response.getStatusCode());
        assertNull(response.getBody());
    }
}
