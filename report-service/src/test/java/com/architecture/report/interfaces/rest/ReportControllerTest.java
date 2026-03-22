package com.architecture.report.interfaces.rest;

import com.architecture.report.application.GenerateReportUseCase;
import com.architecture.report.application.ReportNotFoundException;
import com.architecture.report.domain.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private GenerateReportUseCase generateReportUseCase;

    @InjectMocks
    private ReportController reportController;

    private MockMvc buildMockMvc() {
        return MockMvcBuilders.standaloneSetup(reportController)
                .setControllerAdvice(new ReportExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn200WhenReportExists() throws Exception {
        Report report = Report.builder()
                .id("rpt-200")
                .diagramId("diag-200")
                .content("{\"risks\":[]}")
                .generatedAt(LocalDateTime.now())
                .build();

        when(generateReportUseCase.getReportByDiagramId("diag-200")).thenReturn(report);

        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/api/v1/reports/diag-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rpt-200"))
                .andExpect(jsonPath("$.diagramId").value("diag-200"));
    }

    @Test
    void shouldReturn404WhenReportIsNotFound() throws Exception {
        when(generateReportUseCase.getReportByDiagramId("diag-404"))
                .thenThrow(new ReportNotFoundException("diag-404"));

        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/api/v1/reports/diag-404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn500WhenUnexpectedErrorOccurs() throws Exception {
        when(generateReportUseCase.getReportByDiagramId("diag-500"))
                .thenThrow(new RuntimeException("database timeout"));

        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/api/v1/reports/diag-500"))
                .andExpect(status().isInternalServerError());
    }
}
