package com.architecture.upload.application;

import com.architecture.upload.application.ports.DiagramRepository;
import com.architecture.upload.application.ports.OutboxService;
import com.architecture.upload.application.ports.StorageService;
import com.architecture.upload.domain.Diagram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadUseCase {

    private final StorageService storageService;
    private final DiagramRepository diagramRepository;
    private final OutboxService outboxService;

    @Transactional
    public Diagram uploadDiagram(String originalFileName, InputStream fileStream, long contentLength,
            String contentType, String correlationId) {
        String diagramId = UUID.randomUUID().toString();

        // Determina a extensão de forma segura baseada no Content-Type em vez do nome
        // fornecido pelo usuário
        String fileExtension = getExtensionFromContentType(contentType);
        String s3Key = "diagrams/" + diagramId + fileExtension;

        // 1. Upload to Storage
        storageService.uploadFile(s3Key, fileStream, contentLength, contentType);

        // Registra hook para apagar arquivo do S3 se a transação falhar (ex: falha no
        // banco)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        log.error("Transação no DB falhou, executando rollback para remover arquivo órfão do S3: {}",
                                s3Key);
                        storageService.deleteFile(s3Key);
                    }
                }
            });
        } else {
            log.warn(
                    "Sincronização transacional não está ativa; rollback automático no S3 não poderá ser agendado para a chave {}",
                    s3Key);
        }

        // 2. Save Metadata
        Diagram diagram = Diagram.builder()
                .id(diagramId)
                .originalFileName(originalFileName)
                .s3Key(s3Key)
                .status("RECEIVED")
                .uploadedAt(LocalDateTime.now())
                .build();
        diagramRepository.save(diagram);

        // 3. Enqueue Event in Transactional Outbox
        outboxService.enqueueFileUploadedEvent(diagramId, s3Key, correlationId);

        return diagram;
    }

    private String getExtensionFromContentType(String contentType) {
        if (contentType == null)
            return "";
        switch (contentType) {
            case "image/jpeg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "application/pdf":
                return ".pdf";
            default:
                return "";
        }
    }
}
