package com.example.matching.dto.employee.api;

import java.io.Serializable;

public record CreateVideoInterviewSessionRequest(
        Long empId,
        Long postId,
        String sessionName,
        String interviewMode) implements Serializable {
}
