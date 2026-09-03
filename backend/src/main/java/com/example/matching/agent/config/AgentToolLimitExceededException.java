package com.example.matching.agent.config;

/** Raised when an agent exceeds its configured tool-call budget or deadline. */
public class AgentToolLimitExceededException extends RuntimeException {

    public AgentToolLimitExceededException(String message) {
        super(message);
    }
}
