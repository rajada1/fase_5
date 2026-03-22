package com.architecture.report.application;

import com.architecture.report.application.ports.ReportRepository;
import com.architecture.report.domain.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateReportUseCase {

    private final ReportRepository reportRepository;

    @Transactional
    public Report generateAndSaveReport(String diagramId, String jsonAnalysisContent) {
        try {
            Report report = reportRepository.findByDiagramId(diagramId).orElseGet(() -> Report.builder()
                    .id(UUID.randomUUID().toString())
                    .diagramId(diagramId)
                    .build());

            report.setContent(jsonAnalysisContent);
            report.setGeneratedAt(LocalDateTime.now());

            return reportRepository.save(report);
        } catch (DataIntegrityViolationException ex) {
            Report existingReport = reportRepository.findByDiagramId(diagramId)
                    .orElseThrow(() -> ex);

            existingReport.setContent(jsonAnalysisContent);
            existingReport.setGeneratedAt(LocalDateTime.now());
            return reportRepository.save(existingReport);
        }
    }

    @Transactional(readOnly = true)
    public Report getReportByDiagramId(String diagramId) {
        return reportRepository.findByDiagramId(diagramId)
                .orElseThrow(() -> new ReportNotFoundException(diagramId));
    }
}
