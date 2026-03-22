package com.architecture.report.application;

public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(String diagramId) {
        super("Relatório não encontrado para o diagrama: " + diagramId);
    }
}
