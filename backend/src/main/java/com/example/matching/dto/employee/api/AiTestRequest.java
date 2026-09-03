package com.example.matching.dto.employee.api;

import java.io.Serializable;

public record AiTestRequest(
        Long empId,
        Long postId,
        Long abilityTagId) implements Serializable {
}
