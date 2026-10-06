package com.example.matching.service.kg.build;

public class GraphBuildContext {

    private final String graphVersion;
    private final String validFrom;

    public GraphBuildContext(String graphVersion, String validFrom) {
        this.graphVersion = graphVersion;
        this.validFrom = validFrom;
    }

    public String graphVersion() {
        return graphVersion;
    }

    public String validFrom() {
        return validFrom;
    }
}
