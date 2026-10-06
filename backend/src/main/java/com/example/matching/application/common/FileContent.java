package com.example.matching.application.common;

/**
 * Transport-neutral binary content returned by an application use case.
 */
public record FileContent(String fileName, byte[] content) {
}
