package com.architecture.upload.application.ports;

public interface MessagingService {
    void publishFileUploadedEvent(String diagramId, String s3Key);
}
