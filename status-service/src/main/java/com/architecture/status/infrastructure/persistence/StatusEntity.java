package com.architecture.status.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "statuses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusEntity {
    @Id
    private String diagramId;
    private String state;
    private LocalDateTime lastUpdatedAt;
    
    @jakarta.persistence.Version
    private Long version;
}
