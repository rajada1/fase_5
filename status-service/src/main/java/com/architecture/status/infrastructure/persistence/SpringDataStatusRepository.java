package com.architecture.status.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataStatusRepository extends JpaRepository<StatusEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StatusEntity> findByDiagramId(String diagramId);
}
