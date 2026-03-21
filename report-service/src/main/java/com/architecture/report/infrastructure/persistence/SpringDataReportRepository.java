package com.architecture.report.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataReportRepository extends JpaRepository<ReportEntity, String> {
    Optional<ReportEntity> findByDiagramId(String diagramId);
}
