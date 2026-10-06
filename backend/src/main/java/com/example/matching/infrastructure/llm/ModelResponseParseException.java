package com.example.matching.infrastructure.llm;

/**
 * Thrown when an LLM response cannot be parsed into the expected type.
 * Each use case catches this and produces a fallback result.
 */
public class ModelResponseParseException extends RuntimeException {

    public ModelResponseParseException(String message) {
        super(message);
    }

    public ModelResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
