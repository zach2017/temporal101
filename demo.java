package com.example.docpipeline.exception;

import java.time.Instant;
import java.util.Map;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public sealed abstract class ApplicationException extends RuntimeException
    permits BusinessException, SecurityException, IntegrationException,
            WorkflowException, TransientException {

    private final String errorCode;
    private final ErrorSeverity severity;
    private final Map<String, Object> context;
    private final UUID correlationId;
    private final Instant timestamp;

    protected ApplicationException(String errorCode, String message,
                                   ErrorSeverity severity,
                                   Map<String, Object> context,
                                   Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.severity = severity;
        this.context = context == null ? Map.of() : Map.copyOf(context);
        this.correlationId = CorrelationContext.currentOrNew();
        this.timestamp = Instant.now();
    }

    public abstract boolean isRetryable();
    public abstract HttpStatus suggestedHttpStatus();

    public String getErrorCode() { return errorCode; }
    public ErrorSeverity getSeverity() { return severity; }
    public Map<String, Object> getContext() { return context; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getTimestamp() { return timestamp; }
}

public enum ErrorSeverity { LOW, MEDIUM, HIGH, CRITICAL }
