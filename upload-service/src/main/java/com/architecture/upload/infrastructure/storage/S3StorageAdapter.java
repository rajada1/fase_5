package com.architecture.upload.infrastructure.storage;

import com.architecture.upload.application.ports.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3StorageAdapter implements StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Override
    public String uploadFile(String key, InputStream content, long contentLength, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            if (contentLength < 0) {
                throw new IllegalArgumentException("Tamanho do arquivo inválido para upload: " + contentLength);
            }

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(content, contentLength));
            return key;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao efetuar upload do arquivo para o S3", e);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("Arquivo {} deletado do S3 com sucesso (rollback)", key);
        } catch (Exception e) {
            log.error("Falha ao deletar arquivo do S3 (rollback) key={}: {}", key, e.getMessage());
        }
    }
}
