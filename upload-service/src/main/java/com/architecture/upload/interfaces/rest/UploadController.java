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

        private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB limit

        private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

        public ResponseEntity<UploadResponseDTO> uploadDiagram(MultipartFile file) {
                return uploadDiagram(file, null);
        }

        @PostMapping
        public ResponseEntity<UploadResponseDTO> uploadDiagram(
                        @RequestParam(value = "file", required = false) MultipartFile file,
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

                // 2. Verificar o tamanho máximo do arquivo
                if (file.getSize() > MAX_FILE_SIZE) {
                        log.warn(
                                        "Tentativa de upload excedeu o limite. correlationId={} fileSize={} maxLimit={}",
                                        correlationId,
                                        file.getSize(),
                                        MAX_FILE_SIZE);
                        return ResponseEntity.badRequest().header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(null, "BAD_REQUEST",
                                                        "O tamanho do arquivo excede o limite máximo permitido de 10MB."));
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

                // 4. Validar o Content-Type e Extensão (Segurança contra Spoofing)
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

                String lowerCaseFileName = cleanFileName.toLowerCase();
                boolean isExtensionValid = (contentType.equals("application/pdf") && lowerCaseFileName.endsWith(".pdf"))
                                ||
                                (contentType.equals("image/png") && lowerCaseFileName.endsWith(".png")) ||
                                (contentType.equals("image/jpeg") && (lowerCaseFileName.endsWith(".jpg")
                                                || lowerCaseFileName.endsWith(".jpeg")));

                if (!isExtensionValid) {
                        log.warn(
                                        "Tentativa de upload com extensão incompatível (Spoofing). correlationId={} fileName={} contentType={}",
                                        correlationId,
                                        cleanFileName,
                                        contentType);
                        return ResponseEntity.badRequest().header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(null, "BAD_REQUEST",
                                                        "A extensão do arquivo não corresponde ao tipo de conteúdo enviado (Spoofing detectado)."));
                }

                try {
                        if (!isFileSignatureValid(file, contentType)) {
                                log.warn(
                                                "Tentativa de upload com assinatura incompatível (MIME spoofing). correlationId={} fileName={} contentType={}",
                                                correlationId,
                                                cleanFileName,
                                                contentType);
                                return ResponseEntity.badRequest().header(CORRELATION_ID_HEADER, correlationId)
                                                .body(new UploadResponseDTO(null, "BAD_REQUEST",
                                                                "Conteúdo do arquivo não corresponde ao tipo informado (MIME spoofing detectado)."));
                        }

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
                                        "Recebido");

                        return ResponseEntity.accepted().header(CORRELATION_ID_HEADER, correlationId)
                                        .body(new UploadResponseDTO(
                                                        diagram.getId(),
                                                        "Recebido",
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

        private boolean isFileSignatureValid(MultipartFile file, String contentType) {
                try {
                        byte[] bytes = file.getBytes();

                        if ("application/pdf".equals(contentType)) {
                                return startsWith(bytes, new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D }); // %PDF-
                        }

                        if ("image/png".equals(contentType)) {
                                return startsWith(bytes,
                                                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A });
                        }

                        if ("image/jpeg".equals(contentType)) {
                                return startsWith(bytes, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF });
                        }

                        return false;
                } catch (Exception e) {
                        log.warn("Falha ao validar assinatura do arquivo: {}", e.getMessage());
                        return false;
                }
        }

        private boolean startsWith(byte[] source, byte[] prefix) {
                if (source == null || source.length < prefix.length) {
                        return false;
                }

                for (int index = 0; index < prefix.length; index++) {
                        if (source[index] != prefix[index]) {
                                return false;
                        }
                }

                return true;
        }
}
