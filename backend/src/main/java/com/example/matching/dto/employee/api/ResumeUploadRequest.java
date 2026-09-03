package com.example.matching.dto.employee.api;

import java.io.Serializable;

public record ResumeUploadRequest(
        Long empId) implements Serializable {
}
