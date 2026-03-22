package com.architecture.status.infrastructure.persistence;

import com.architecture.status.application.ports.StatusRepository;
import com.architecture.status.domain.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StatusRepositoryAdapter implements StatusRepository {

    private final SpringDataStatusRepository repository;

    @Override
    public Status save(Status status) {
        StatusEntity entity = StatusEntity.builder()
                .diagramId(status.getDiagramId())
                .state(status.getState())
                .lastUpdatedAt(status.getLastUpdatedAt())
                .version(status.getVersion())
                .build();

        StatusEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Status> findByDiagramId(String diagramId) {
        return repository.findById(diagramId).map(this::toDomain);
    }

    @Override
    public Optional<Status> findByDiagramIdForUpdate(String diagramId) {
        return repository.findByDiagramId(diagramId).map(this::toDomain);
    }

    private Status toDomain(StatusEntity entity) {
        return Status.builder()
                .diagramId(entity.getDiagramId())
                .state(entity.getState())
                .lastUpdatedAt(entity.getLastUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}
