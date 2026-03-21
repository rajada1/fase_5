package com.architecture.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/upload")
    public ResponseEntity<String> uploadFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("O Serviço de Upload está indisponível no momento devido à alta carga ou falhas. Por favor, tente novamente mais tarde.");
    }

    @RequestMapping("/report")
    public ResponseEntity<String> reportFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("O Serviço de Relatórios está indisponível no momento. Por favor, tente novamente mais tarde.");
    }

    @RequestMapping("/status")
    public ResponseEntity<String> statusFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("O Serviço de Status está indisponível no momento. Por favor, tente novamente mais tarde.");
    }
}
