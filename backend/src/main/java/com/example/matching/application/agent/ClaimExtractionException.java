package com.example.matching.application.agent;

/**
 * Signals that claim extraction could not be completed. Callers must not
 * treat this as a successful extraction with zero claims.
 */
public class ClaimExtractionException extends RuntimeException {

    public ClaimExtractionException(String message) {
        super(message);
    }

    public ClaimExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
