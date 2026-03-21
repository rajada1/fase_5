package com.architecture.upload.infrastructure.messaging;

import com.architecture.upload.application.ports.MessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Component
@RequiredArgsConstructor
public class SnsMessagingAdapter implements MessagingService {

    private final SnsClient snsClient;

    @Value("${aws.sns.file-uploaded-topic-arn}")
    private String topicArn;

    @Override
    public void publishFileUploadedEvent(String diagramId, String s3Key) {
        String message = String.format("{\"diagramId\":\"%s\", \"s3Key\":\"%s\", \"eventType\":\"FILE_UPLOADED\"}",
                diagramId, s3Key);

        PublishRequest request = PublishRequest.builder()
                .message(message)
                .topicArn(topicArn)
                .build();

        snsClient.publish(request);
    }
}
