package com.architecture.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackControllerTest {

    private final FallbackController fallbackController = new FallbackController();

    @Test
    void shouldReturn503ForUploadFallback() {
        ResponseEntity<String> response = fallbackController.uploadFallback();

        assertEquals(HttpStatusCode.valueOf(503), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Upload"));
    }

    @Test
    void shouldReturn503ForReportFallback() {
        ResponseEntity<String> response = fallbackController.reportFallback();

        assertEquals(HttpStatusCode.valueOf(503), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Relatórios"));
    }

    @Test
    void shouldReturn503ForStatusFallback() {
        ResponseEntity<String> response = fallbackController.statusFallback();

        assertEquals(HttpStatusCode.valueOf(503), response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Status"));
    }
}
