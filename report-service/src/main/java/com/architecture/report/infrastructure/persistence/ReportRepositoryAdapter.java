package com.architecture.report.infrastructure.persistence;

import com.architecture.report.application.ports.ReportRepository;
import com.architecture.report.domain.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReportRepositoryAdapter implements ReportRepository {

    private final SpringDataReportRepository repository;

    @Override
    public Report save(Report report) {
        ReportEntity entity = ReportEntity.builder()
                .id(report.getId())
                .diagramId(report.getDiagramId())
                .content(report.getContent())
                .generatedAt(report.getGeneratedAt())
                .build();

        ReportEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Report> findByDiagramId(String diagramId) {
        return repository.findTopByDiagramIdOrderByGeneratedAtDesc(diagramId).map(this::toDomain);
    }

    private Report toDomain(ReportEntity entity) {
        return Report.builder()
                .id(entity.getId())
                .diagramId(entity.getDiagramId())
                .content(entity.getContent())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }
}
