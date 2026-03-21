package com.architecture.status.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Status {
    private String diagramId;
    private String state; // RECEIVED, PROCESSING, ANALYZED, ERROR
    private LocalDateTime lastUpdatedAt;
    private Long version;
}
