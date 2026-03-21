package com.architecture.upload.application.ports;

import com.architecture.upload.domain.Diagram;
import java.util.Optional;

public interface DiagramRepository {
    Diagram save(Diagram diagram);

    Optional<Diagram> findById(String id);
}
