package com.architecture.upload.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "diagrams")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramEntity {
    @Id
    private String id;
    private String originalFileName;
    private String s3Key;
    private String status;
    private LocalDateTime uploadedAt;
}
