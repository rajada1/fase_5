package com.architecture.upload.application.ports;

public interface OutboxService {
    void enqueueFileUploadedEvent(String diagramId, String s3Key, String correlationId);
}
