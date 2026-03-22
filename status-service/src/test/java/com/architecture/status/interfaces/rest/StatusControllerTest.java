package com.architecture.status.interfaces.rest;

import com.architecture.status.application.StatusNotFoundException;
import com.architecture.status.application.UpdateStatusUseCase;
import com.architecture.status.domain.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

    @Mock
    private UpdateStatusUseCase updateStatusUseCase;

    @InjectMocks
    private StatusController statusController;

    private MockMvc buildMockMvc() {
        return MockMvcBuilders.standaloneSetup(statusController)
                .setControllerAdvice(new StatusExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn200WhenStatusExists() throws Exception {
        Status status = Status.builder()
                .diagramId("diag-200")
                .state("PROCESSING")
                .lastUpdatedAt(LocalDateTime.now())
                .build();

        when(updateStatusUseCase.getStatus("diag-200")).thenReturn(status);

        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/api/v1/status/diag-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagramId").value("diag-200"))
                .andExpect(jsonPath("$.state").value("PROCESSING"));
    }

    @Test
    void shouldReturn404WhenStatusIsNotFound() throws Exception {
        when(updateStatusUseCase.getStatus("diag-404"))
                .thenThrow(new StatusNotFoundException("diag-404"));

        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/api/v1/status/diag-404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn500WhenUnexpectedErrorOccurs() throws Exception {
        when(updateStatusUseCase.getStatus("diag-500"))
                .thenThrow(new RuntimeException("database timeout"));

        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/api/v1/status/diag-500"))
                .andExpect(status().isInternalServerError());
    }
}
