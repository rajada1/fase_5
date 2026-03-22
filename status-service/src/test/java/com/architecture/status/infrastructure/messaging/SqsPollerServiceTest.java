package com.architecture.status.infrastructure.messaging;

import com.architecture.status.application.UpdateStatusUseCase;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsPollerServiceTest {

        @Mock
        private SqsClient sqsClient;

        @Mock
        private UpdateStatusUseCase updateStatusUseCase;

        @Test
        void shouldDeleteMessageAndSkipProcessingWhenDiagramIdIsMissing() throws Exception {
                SqsPollerService service = new SqsPollerService(sqsClient, updateStatusUseCase);

                Message message = Message.builder()
                                .body("{\"eventType\":\"FILE_UPLOADED\"}")
                                .receiptHandle("receipt-123")
                                .build();

                Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class, String.class,
                                String.class);
                method.setAccessible(true);
                method.invoke(service, message, "queue-url", "Em processamento");

                verify(updateStatusUseCase, never()).updateStatus(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.anyString());
                verify(sqsClient)
                                .deleteMessage(org.mockito.ArgumentMatchers
                                                .argThat(matchesDeleteRequest("queue-url", "receipt-123")));
        }

        @Test
        void shouldProcessSnsWrappedPayloadWhenDiagramIdIsPresent() throws Exception {
                SqsPollerService service = new SqsPollerService(sqsClient, updateStatusUseCase);

                Message message = Message.builder()
                                .body("{\"Message\":\"{\\\"diagramId\\\":\\\"diag-123\\\"}\"}")
                                .receiptHandle("receipt-234")
                                .build();

                Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class, String.class,
                                String.class);
                method.setAccessible(true);
                method.invoke(service, message, "queue-url", "Em processamento");

                verify(updateStatusUseCase).updateStatus("diag-123", "Em processamento");
                verify(sqsClient)
                                .deleteMessage(org.mockito.ArgumentMatchers
                                                .argThat(matchesDeleteRequest("queue-url", "receipt-234")));
        }

        @Test
        void shouldDeleteMessageAndSkipProcessingWhenDiagramIdIsBlank() throws Exception {
                SqsPollerService service = new SqsPollerService(sqsClient, updateStatusUseCase);

                Message message = Message.builder()
                                .body("{\"diagramId\":\"   \"}")
                                .receiptHandle("receipt-345")
                                .build();

                Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class, String.class,
                                String.class);
                method.setAccessible(true);
                method.invoke(service, message, "queue-url", "Em processamento");

                verify(updateStatusUseCase, never()).updateStatus(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.anyString());
                verify(sqsClient)
                                .deleteMessage(org.mockito.ArgumentMatchers
                                                .argThat(matchesDeleteRequest("queue-url", "receipt-345")));
        }

        @Test
        void shouldNotDeleteOrProcessWhenRootJsonIsMalformed() throws Exception {
                SqsPollerService service = new SqsPollerService(sqsClient, updateStatusUseCase);

                Message message = Message.builder()
                                .body("{invalid-json")
                                .receiptHandle("receipt-901")
                                .build();

                Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class, String.class,
                                String.class);
                method.setAccessible(true);
                method.invoke(service, message, "queue-url", "Em processamento");

                verify(updateStatusUseCase, never()).updateStatus(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.anyString());
                verify(sqsClient)
                                .deleteMessage(org.mockito.ArgumentMatchers
                                                .argThat(matchesDeleteRequest("queue-url", "receipt-901")));
        }

        @Test
        void shouldNotDeleteOrProcessWhenSnsWrappedJsonIsMalformed() throws Exception {
                SqsPollerService service = new SqsPollerService(sqsClient, updateStatusUseCase);

                Message message = Message.builder()
                                .body("{\"Message\":\"{not-valid}\"}")
                                .receiptHandle("receipt-902")
                                .build();

                Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class, String.class,
                                String.class);
                method.setAccessible(true);
                method.invoke(service, message, "queue-url", "Em processamento");

                verify(updateStatusUseCase, never()).updateStatus(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.anyString());
                verify(sqsClient)
                                .deleteMessage(org.mockito.ArgumentMatchers
                                                .argThat(matchesDeleteRequest("queue-url", "receipt-902")));
        }

        @Test
        void shouldNotDeleteMessageWhenUpdateStatusThrows() throws Exception {
                SqsPollerService service = new SqsPollerService(sqsClient, updateStatusUseCase);
                doThrow(new RuntimeException("db unavailable"))
                                .when(updateStatusUseCase)
                                .updateStatus("diag-500", "Em processamento");

                Message message = Message.builder()
                                .body("{\"diagramId\":\"diag-500\"}")
                                .receiptHandle("receipt-905")
                                .build();

                Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class, String.class,
                                String.class);
                method.setAccessible(true);
                method.invoke(service, message, "queue-url", "Em processamento");

                verify(updateStatusUseCase).updateStatus("diag-500", "Em processamento");
                verify(sqsClient, never()).deleteMessage(org.mockito.ArgumentMatchers.any(DeleteMessageRequest.class));
        }

        @Test
        void shouldMapFailedEventToErrorState() throws Exception {
                SqsPollerService service = new SqsPollerService(sqsClient, updateStatusUseCase);

                Message message = Message.builder()
                                .body("{\"diagramId\":\"diag-err\",\"eventType\":\"ANALYSIS_FAILED\"}")
                                .receiptHandle("receipt-999")
                                .build();

                Method method = SqsPollerService.class.getDeclaredMethod("processMessage", Message.class, String.class,
                                String.class);
                method.setAccessible(true);
                method.invoke(service, message, "queue-url", "Analisado");

                verify(updateStatusUseCase).updateStatus("diag-err", "Erro");
                verify(sqsClient)
                                .deleteMessage(org.mockito.ArgumentMatchers
                                                .argThat(matchesDeleteRequest("queue-url", "receipt-999")));
        }

        @Test
        void shouldNotThrowWhenReceiveMessageFailsInPollQueue() {
                SqsPollerService service = new SqsPollerService(sqsClient, updateStatusUseCase);
                ReflectionTestUtils.setField(service, "uploadedQueueUrl", "queue-uploaded");

                when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                                .thenThrow(new RuntimeException("sqs unavailable"));

                assertDoesNotThrow(service::pollUploadedQueue);

                verify(updateStatusUseCase, never()).updateStatus(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.anyString());
                verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        }

        private ArgumentMatcher<DeleteMessageRequest> matchesDeleteRequest(String queueUrl, String receiptHandle) {
                return request -> request != null
                                && queueUrl.equals(request.queueUrl())
                                && receiptHandle.equals(request.receiptHandle());
        }
}
