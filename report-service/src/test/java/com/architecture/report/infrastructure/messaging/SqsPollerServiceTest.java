package com.architecture.report.infrastructure.messaging;

import com.architecture.report.application.GenerateReportUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsPollerServiceTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private GenerateReportUseCase generateReportUseCase;

    @Test
    void shouldDeleteMessageAndSkipProcessingWhenAnalysisIsMissing() throws Exception {
        SqsPollerService service = new SqsPollerService(sqsClient, generateReportUseCase);
        ReflectionTestUtils.setField(service, "queueUrl", "queue-url");

        Message message = Message.builder()
                .body("{\"diagramId\":\"diag-123\"}")
                .receiptHandle("receipt-456")
                .build();

        Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class);
        method.setAccessible(true);
        method.invoke(service, message);

        verify(generateReportUseCase, never()).generateAndSaveReport(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sqsClient)
                .deleteMessage(org.mockito.ArgumentMatchers.argThat(matchesDeleteRequest("queue-url", "receipt-456")));
    }

    @Test
    void shouldProcessSnsWrappedPayloadWhenAllFieldsArePresent() throws Exception {
        SqsPollerService service = new SqsPollerService(sqsClient, generateReportUseCase);
        ReflectionTestUtils.setField(service, "queueUrl", "queue-url");

        Message message = Message.builder()
                .body("{\"Message\":\"{\\\"diagramId\\\":\\\"diag-789\\\",\\\"analysis\\\":{\\\"risks\\\":[]}}\"}")
                .receiptHandle("receipt-567")
                .build();

        Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class);
        method.setAccessible(true);
        method.invoke(service, message);

        verify(generateReportUseCase).generateAndSaveReport("diag-789", "{\"risks\":[]}");
        verify(sqsClient)
                .deleteMessage(org.mockito.ArgumentMatchers.argThat(matchesDeleteRequest("queue-url", "receipt-567")));
    }

    @Test
    void shouldDeleteMessageAndSkipProcessingWhenDiagramIdIsBlank() throws Exception {
        SqsPollerService service = new SqsPollerService(sqsClient, generateReportUseCase);
        ReflectionTestUtils.setField(service, "queueUrl", "queue-url");

        Message message = Message.builder()
                .body("{\"diagramId\":\" \",\"analysis\":{\"components\":[]}}")
                .receiptHandle("receipt-678")
                .build();

        Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class);
        method.setAccessible(true);
        method.invoke(service, message);

        verify(generateReportUseCase, never()).generateAndSaveReport(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sqsClient)
                .deleteMessage(org.mockito.ArgumentMatchers.argThat(matchesDeleteRequest("queue-url", "receipt-678")));
    }

    @Test
    void shouldNotDeleteOrProcessWhenRootJsonIsMalformed() throws Exception {
        SqsPollerService service = new SqsPollerService(sqsClient, generateReportUseCase);
        ReflectionTestUtils.setField(service, "queueUrl", "queue-url");

        Message message = Message.builder()
                .body("{invalid-json")
                .receiptHandle("receipt-903")
                .build();

        Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class);
        method.setAccessible(true);
        method.invoke(service, message);

        verify(generateReportUseCase, never()).generateAndSaveReport(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sqsClient, never()).deleteMessage(org.mockito.ArgumentMatchers.any(DeleteMessageRequest.class));
    }

    @Test
    void shouldNotDeleteOrProcessWhenSnsWrappedJsonIsMalformed() throws Exception {
        SqsPollerService service = new SqsPollerService(sqsClient, generateReportUseCase);
        ReflectionTestUtils.setField(service, "queueUrl", "queue-url");

        Message message = Message.builder()
                .body("{\"Message\":\"{not-valid}\"}")
                .receiptHandle("receipt-904")
                .build();

        Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class);
        method.setAccessible(true);
        method.invoke(service, message);

        verify(generateReportUseCase, never()).generateAndSaveReport(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sqsClient, never()).deleteMessage(org.mockito.ArgumentMatchers.any(DeleteMessageRequest.class));
    }

    @Test
    void shouldNotDeleteMessageWhenGenerateReportThrows() throws Exception {
        SqsPollerService service = new SqsPollerService(sqsClient, generateReportUseCase);
        ReflectionTestUtils.setField(service, "queueUrl", "queue-url");

        when(generateReportUseCase.generateAndSaveReport("diag-501", "{\"components\":[]}"))
                .thenThrow(new RuntimeException("db unavailable"));

        Message message = Message.builder()
                .body("{\"diagramId\":\"diag-501\",\"analysis\":{\"components\":[]}}")
                .receiptHandle("receipt-906")
                .build();

        Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class);
        method.setAccessible(true);
        method.invoke(service, message);

        verify(generateReportUseCase).generateAndSaveReport("diag-501", "{\"components\":[]}");
        verify(sqsClient, never()).deleteMessage(org.mockito.ArgumentMatchers.any(DeleteMessageRequest.class));
    }

    @Test
    void shouldNotThrowWhenReceiveMessageFailsInPollMessages() {
        SqsPollerService service = new SqsPollerService(sqsClient, generateReportUseCase);
        ReflectionTestUtils.setField(service, "queueUrl", "queue-url");

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new RuntimeException("sqs unavailable"));

        assertDoesNotThrow(service::pollMessages);

        verify(generateReportUseCase, never()).generateAndSaveReport(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    private ArgumentMatcher<DeleteMessageRequest> matchesDeleteRequest(String queueUrl, String receiptHandle) {
        return request -> request != null
                && queueUrl.equals(request.queueUrl())
                && receiptHandle.equals(request.receiptHandle());
    }
}
