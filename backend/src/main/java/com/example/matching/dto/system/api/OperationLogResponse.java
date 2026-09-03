package com.example.matching.dto.system.api;

import java.time.LocalDateTime;

public record OperationLogResponse(
    Long id,
    Long userId,
    String realName,
    String operationModule,
    String operationType,
    String operationDesc,
    String requestMethod,
    String requestUrl,
    String requestParams,
    String responseResult,
    String operationIp,
    LocalDateTime operationTime,
    Long costTime
) {}
