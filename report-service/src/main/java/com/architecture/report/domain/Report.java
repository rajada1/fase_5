package com.architecture.report.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {
    private String id;
    private String diagramId;
    private String content; // JSON formatted string or complex structured object
    private LocalDateTime generatedAt;
}
