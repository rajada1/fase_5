package com.architecture.report.interfaces.rest;

import com.architecture.report.application.GenerateReportUseCase;
import com.architecture.report.domain.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final GenerateReportUseCase generateReportUseCase;

    @GetMapping("/{diagramId}")
    public ResponseEntity<ReportResponseDTO> getReport(@PathVariable String diagramId) {
        try {
            Report report = generateReportUseCase.getReportByDiagramId(diagramId);

            ReportResponseDTO response = ReportResponseDTO.builder()
                    .id(report.getId())
                    .diagramId(report.getDiagramId())
                    .content(report.getContent())
                    .generatedAt(report.getGeneratedAt())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Relatório não encontrado")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }
}
