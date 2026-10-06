package com.example.matching.event;

public record MatchingTaskFailedEvent(String taskId, String reason) {
}
