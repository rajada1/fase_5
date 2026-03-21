package com.architecture.upload.infrastructure.persistence;

import com.architecture.upload.application.ports.DiagramRepository;
import com.architecture.upload.domain.Diagram;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DiagramRepositoryAdapter implements DiagramRepository {

    private final SpringDataDiagramRepository springDataDiagramRepository;

    @Override
    public Diagram save(Diagram diagram) {
        DiagramEntity entity = DiagramEntity.builder()
                .id(diagram.getId())
                .originalFileName(diagram.getOriginalFileName())
                .s3Key(diagram.getS3Key())
                .status(diagram.getStatus())
                .uploadedAt(diagram.getUploadedAt())
                .build();

        DiagramEntity savedInfo = springDataDiagramRepository.save(entity);
        return toDomain(savedInfo);
    }

    @Override
    public Optional<Diagram> findById(String id) {
        return springDataDiagramRepository.findById(id).map(this::toDomain);
    }

    private Diagram toDomain(DiagramEntity entity) {
        return Diagram.builder()
                .id(entity.getId())
                .originalFileName(entity.getOriginalFileName())
                .s3Key(entity.getS3Key())
                .status(entity.getStatus())
                .uploadedAt(entity.getUploadedAt())
                .build();
    }
}
