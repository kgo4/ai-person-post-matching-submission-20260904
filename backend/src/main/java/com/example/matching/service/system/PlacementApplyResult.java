package com.example.matching.service.system;

/** Outcome returned by the controlled placement-application endpoint. */
public record PlacementApplyResult(String status, Long finalTagId) {
}
