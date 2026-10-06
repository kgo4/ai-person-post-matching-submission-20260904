package com.example.matching.dto.closure;

import lombok.Data;

import java.io.Serializable;

@Data
public class CapabilityClosureResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventType;

    private String sourceType;

    private Long sourceRefId;

    private String businessKey;

    private String closureStatus;

    private Integer evidenceCount;

    private Integer knowledgeDocCount;

    private String graphRefreshStatus;

    private String message;
}
