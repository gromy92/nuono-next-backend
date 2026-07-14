package com.nuono.next.web;

import com.nuono.next.noon.NoonOperationException;
import com.nuono.next.noon.NoonResponseClassification;
import com.nuono.next.noon.NoonResponseClassifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String category;
    private final String operation;
    private final boolean retryable;
    private final boolean partialSuccess;
    private final String reference;
    private final Map<String, Object> details;

    public ApiProblemException(
            HttpStatus status,
            String code,
            String category,
            String operation,
            String message,
            boolean retryable,
            boolean partialSuccess,
            String reference,
            Map<String, Object> details,
            Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.category = category;
        this.operation = operation;
        this.retryable = retryable;
        this.partialSuccess = partialSuccess;
        this.reference = reference;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ApiProblemException fromNoon(NoonOperationException exception) {
        NoonResponseClassification classification = exception.getClassification();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("recognitionRuleVersion", NoonResponseClassifier.RULESET_VERSION);
        details.put("providerStatus", classification.getProviderStatus());
        if (!classification.getAffectedPskuCodes().isEmpty()) {
            details.put("affectedPskuCodes", classification.getAffectedPskuCodes());
        }
        return new ApiProblemException(
                HttpStatus.valueOf(classification.getApiStatus()),
                classification.getCode(),
                classification.getCategory().name(),
                classification.getOperation(),
                classification.getUserMessage(),
                classification.isRetryable(),
                false,
                null,
                details,
                exception
        );
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getCategory() {
        return category;
    }

    public String getOperation() {
        return operation;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isPartialSuccess() {
        return partialSuccess;
    }

    public String getReference() {
        return reference;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
