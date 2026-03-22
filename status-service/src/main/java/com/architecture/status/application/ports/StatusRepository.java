package com.architecture.status.application.ports;

import com.architecture.status.domain.Status;
import java.util.Optional;

public interface StatusRepository {
    Status save(Status status);

    Optional<Status> findByDiagramId(String diagramId);

    Optional<Status> findByDiagramIdForUpdate(String diagramId);
}
