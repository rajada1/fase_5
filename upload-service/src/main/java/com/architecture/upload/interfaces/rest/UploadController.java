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

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final UploadUseCase uploadUseCase;

    // Restringindo os tipos de arquivos aceitos por segurança
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "application/pdf");

    @PostMapping
    public ResponseEntity<UploadResponseDTO> uploadDiagram(@RequestParam("file") MultipartFile file) {

        // 1. Verificar se o arquivo é nulo ou vazio
        if (file == null || file.isEmpty()) {
            log.warn("Tentativa de upload de arquivo vazio ou nulo");
            return ResponseEntity.badRequest()
                    .body(new UploadResponseDTO(null, "BAD_REQUEST", "O arquivo não pode ser vazio."));
        }

        // 2 & 3. Sanitização do nome do arquivo e prevenção de Path Traversal
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            log.warn("Tentativa de upload sem nome de arquivo");
            return ResponseEntity.badRequest()
                    .body(new UploadResponseDTO(null, "BAD_REQUEST", "Nome de arquivo inválido ou ausente."));
        }

        String cleanFileName = StringUtils.cleanPath(originalFilename);
        if (cleanFileName.contains("..")) {
            log.warn("Tentativa de Path Traversal detectada e abortada: {}", cleanFileName);
            return ResponseEntity.badRequest()
                    .body(new UploadResponseDTO(null, "BAD_REQUEST", "Nome de arquivo contém um caminho inválido."));
        }

        // 4. Validar o Content-Type (Segurança)
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            log.warn("Tentativa de upload de tipo não suportado: {}", contentType);
            return ResponseEntity.badRequest()
                    .body(new UploadResponseDTO(null, "BAD_REQUEST",
                            "Tipo de arquivo não suportado. Apenas imagens e PDF são permitidos."));
        }

        try {
            Diagram diagram = uploadUseCase.uploadDiagram(
                    cleanFileName,
                    file.getInputStream(),
                    file.getSize(),
                    contentType);

            // 6. Prevenção de NullPointerException reverso
            if (diagram == null || diagram.getId() == null) {
                log.error("UploadUseCase falhou silenciosamente, retornou diagrama ou ID nulo.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new UploadResponseDTO(null, "ERROR",
                                "Erro interno: Falha ao gerar o identificador do diagrama."));
            }

            return ResponseEntity.accepted().body(new UploadResponseDTO(
                    diagram.getId(),
                    "RECEIVED",
                    "Arquivo enviado com sucesso e processamento iniciado."));

        } catch (Exception e) {
            // 5 & 8. Não expor a stack de erro diretamente para o cliente, manter no log do
            // servidor
            log.error("Erro inesperado ao processar upload do arquivo: {}", cleanFileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponseDTO(null, "ERROR",
                            "Ocorreu um erro interno no servidor ao processar o seu arquivo."));
        }
    }
}
