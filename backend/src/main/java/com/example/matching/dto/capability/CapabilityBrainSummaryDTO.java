package com.example.matching.dto.capability;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CapabilityBrainSummaryDTO {

    private String title;
    private String mission;
    private int loopScore;
    private Health health;
    private List<Stage> stages = new ArrayList<>();
    private List<ModuleLink> moduleLinks = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class Health {
        private long postCount;
        private long employeeCount;
        private long abilityTagCount;
        private long matchingRecordCount;
        private long evidenceCount;
        private long ragDocumentCount;
        private long graphNodeCount;
        private long graphEdgeCount;
        private long evolutionTaskCount;
        private long learningResourceCount;
    }

    @Data
    public static class Stage {
        private String key;
        private String title;
        private String description;
        private String status;
        private String route;
        private String output;
    }

    @Data
    public static class ModuleLink {
        private String key;
        private String title;
        private String description;
        private String route;
        private String role;
    }
}
