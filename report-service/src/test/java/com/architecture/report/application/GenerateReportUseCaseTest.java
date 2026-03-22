package com.architecture.report.application;

import com.architecture.report.application.ports.ReportRepository;
import com.architecture.report.domain.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateReportUseCaseTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private GenerateReportUseCase generateReportUseCase;

    @Test
    void shouldUpdateExistingReportForSameDiagramId() {
        Report existing = Report.builder()
                .id("report-existing-id")
                .diagramId("diag-123")
                .content("{\"old\":true}")
                .generatedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(reportRepository.findByDiagramId("diag-123")).thenReturn(Optional.of(existing));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Report result = generateReportUseCase.generateAndSaveReport("diag-123", "{\"new\":true}");

        assertNotNull(result);
        assertEquals("report-existing-id", result.getId());
        assertEquals("diag-123", result.getDiagramId());
        assertEquals("{\"new\":true}", result.getContent());

        verify(reportRepository).findByDiagramId("diag-123");
        verify(reportRepository).save(any(Report.class));
    }
}
