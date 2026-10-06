package com.example.matching.event;

/**
 * Published after a knowledge document has been persisted and needs its RAG index rebuilt.
 */
public record RagKnowledgeDocumentSavedEvent(Long documentId) {
}
