package com.architecture.report.application.ports;

import com.architecture.report.domain.Report;
import java.util.Optional;

public interface ReportRepository {
    Report save(Report report);

    Optional<Report> findByDiagramId(String diagramId);
}
