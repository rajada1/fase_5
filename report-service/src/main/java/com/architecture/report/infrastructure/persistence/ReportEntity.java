package com.architecture.report.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportEntity {
    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String diagramId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime generatedAt;
}
