package com.example.matching.service.matching.algorithm;

import java.util.List;

@lombok.Data
public class HardConditionResult {
    private boolean passed;
    private List<ConditionDetail> details;
}
