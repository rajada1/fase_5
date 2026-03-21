package com.architecture.upload.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Diagram {
    private String id;
    private String originalFileName;
    private String s3Key;
    private String status;
    private LocalDateTime uploadedAt;
}
