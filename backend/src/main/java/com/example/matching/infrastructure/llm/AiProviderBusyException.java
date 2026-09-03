package com.example.matching.infrastructure.llm;

/** Raised when the configured provider concurrency queue cannot accept another call in time. */
public class AiProviderBusyException extends RuntimeException {

    public AiProviderBusyException(String message) {
        super(message);
    }
}
