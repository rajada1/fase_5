package com.architecture.upload.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataDiagramRepository extends JpaRepository<DiagramEntity, String> {
}
