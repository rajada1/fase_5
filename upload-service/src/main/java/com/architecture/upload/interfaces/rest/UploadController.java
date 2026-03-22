package com.architecture.upload.interfaces.rest;

import com.architecture.upload.application.UploadUseCase;
import com.architecture.upload.domain.Diagram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

        private final UploadUseCase uploadUseCase;

        // Restringindo os tipos de arquivos aceitos por segurança
        private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
                        "image/jpeg", "image/png", "application/pdf");

        private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

        public ResponseEntity<UploadResponseDTO> uploadDiagram(MultipartFile file) {
                return uploadDiagram(file, null);
        }

        @PostMapping
        public ResponseEntity<UploadResponseDTO> uploadDiagram(
                        @RequestParam("file") MultipartFile file,
                        @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationIdHeader) {
                String correlationId = resolveCorrelationId(correlationIdHeader);
                String originalFilename = file != null ? file.getOriginalFilename() : "";
                String contentType = file != null ? file.getContentType() : "";
                long fileSize = file != null ? file.getSize() : 0L;

                log.info(
                                "Requisição de upload recebida. correlationId={} fileName={} contentType={} fileSize={}",
                                correlationId,
                                originalFilename,
                                contentType,
                                fileSize);

                // 1. Verificar se o arquivo é nulo ou vazio
                if (file == null || file.isEmpty()) {
                        log.warn(
                                        "Tentativa de upload inválida. correlationId={} reason=empty_or_null_file fileName={} contentType={} fileSize={}",
                                        correlationId,
                                        originalFilename,
                                        contentType,
                                        fileSize);
                        return ResponseEntity.badRequest().header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(null, "BAD_REQUEST",
                                                        "O arquivo não pode ser vazio."));
                }

                // 2 & 3. Sanitização do nome do arquivo e prevenção de Path Traversal
                if (originalFilename == null || originalFilename.trim().isEmpty()) {
                        log.warn("Tentativa de upload inválida. correlationId={} reason=missing_filename contentType={} fileSize={}",
                                        correlationId,
                                        contentType,
                                        fileSize);
                        return ResponseEntity.badRequest().header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(null, "BAD_REQUEST",
                                                        "Nome de arquivo inválido ou ausente."));
                }

                String cleanFileName = StringUtils.cleanPath(originalFilename);
                if (cleanFileName.contains("..")) {
                        log.warn(
                                        "Tentativa de upload inválida. correlationId={} reason=path_traversal fileName={} contentType={} fileSize={}",
                                        correlationId,
                                        cleanFileName,
                                        contentType,
                                        fileSize);
                        return ResponseEntity.badRequest().header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(null, "BAD_REQUEST",
                                                        "Nome de arquivo contém um caminho inválido."));
                }

                // 4. Validar o Content-Type (Segurança)
                if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
                        log.warn(
                                        "Tentativa de upload inválida. correlationId={} reason=unsupported_content_type fileName={} contentType={} fileSize={}",
                                        correlationId,
                                        cleanFileName,
                                        contentType,
                                        fileSize);
                        return ResponseEntity.badRequest().header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(null, "BAD_REQUEST",
                                                        "Tipo de arquivo não suportado. Apenas imagens e PDF são permitidos."));
                }

                try {
                        Diagram diagram = uploadUseCase.uploadDiagram(
                                        cleanFileName,
                                        file.getInputStream(),
                                        file.getSize(),
                                        contentType,
                                        correlationId);

                        // 6. Prevenção de NullPointerException reverso
                        if (diagram == null || diagram.getId() == null) {
                                log.error(
                                                "Falha ao processar upload. correlationId={} reason=null_diagram_or_id fileName={} contentType={} fileSize={}",
                                                correlationId,
                                                cleanFileName,
                                                contentType,
                                                fileSize);
                                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .header(CORRELATION_ID_HEADER, correlationId)
                                                .body(new UploadResponseDTO(null, "ERROR",
                                                                "Erro interno: Falha ao gerar o identificador do diagrama."));
                        }

                        log.info(
                                        "Upload aceito para processamento. correlationId={} diagramId={} fileName={} contentType={} fileSize={} status={}",
                                        correlationId,
                                        diagram.getId(),
                                        cleanFileName,
                                        contentType,
                                        fileSize,
                                        "RECEIVED");

                        return ResponseEntity.accepted().header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(
                                                        diagram.getId(),
                                                        "RECEIVED",
                                                        "Arquivo enviado com sucesso e processamento iniciado."));

                } catch (Exception e) {
                        // 5 & 8. Não expor a stack de erro diretamente para o cliente, manter no log do
                        // servidor
                        log.error(
                                        "Erro inesperado ao processar upload. correlationId={} fileName={} contentType={} fileSize={} error={}",
                                        correlationId,
                                        cleanFileName,
                                        contentType,
                                        fileSize,
                                        e.getMessage(),
                                        e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(null, "ERROR",
                                                        "Ocorreu um erro interno no servidor ao processar o seu arquivo."));
                }
        }

        private String resolveCorrelationId(String correlationIdHeader) {
                if (correlationIdHeader == null || correlationIdHeader.isBlank()) {
                        return UUID.randomUUID().toString();
                }
                return correlationIdHeader.trim();
        }
}
