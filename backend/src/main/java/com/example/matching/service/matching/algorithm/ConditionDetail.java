package com.example.matching.service.matching.algorithm;

@lombok.Data
public class ConditionDetail {
    private String field;
    private String label;
    private String operator;
    private String expectedValue;
    private String actualValue;
    private boolean passed;
    private String source;
}
