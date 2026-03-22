package com.architecture.status.application;

public class StatusNotFoundException extends RuntimeException {

    public StatusNotFoundException(String diagramId) {
        super("Status não encontrado para o diagrama: " + diagramId);
    }
}
