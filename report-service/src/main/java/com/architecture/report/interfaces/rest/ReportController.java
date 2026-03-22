package com.architecture.report.interfaces.rest;

import com.architecture.report.application.GenerateReportUseCase;
import com.architecture.report.domain.Report;
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
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final GenerateReportUseCase generateReportUseCase;

    @GetMapping("/{diagramId}")
    public ResponseEntity<ReportResponseDTO> getReport(
            @PathVariable("diagramId") String diagramId,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationIdHeader) {
        String correlationId = resolveCorrelationId(correlationIdHeader);

        log.info("Consulta de relatório recebida. correlationId={} diagramId={}", correlationId, diagramId);

        Report report = generateReportUseCase.getReportByDiagramId(diagramId);

        ReportResponseDTO response = ReportResponseDTO.builder()
                .id(report.getId())
                .diagramId(report.getDiagramId())
                .content(report.getContent())
                .generatedAt(report.getGeneratedAt())
                .build();

        log.info("Consulta de relatório concluída. correlationId={} diagramId={} reportId={}", correlationId,
                response.getDiagramId(),
                response.getId());

        return ResponseEntity.ok().header(CORRELATION_ID_HEADER, correlationId).body(response);
    }

    private String resolveCorrelationId(String correlationIdHeader) {
        if (correlationIdHeader == null || correlationIdHeader.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationIdHeader.trim();
    }
}
