package com.example.matching.service.kg;

import java.util.Map;

public interface GraphRelationGovernanceService {

    Map<String, Object> getPolicies();

    Map<String, Object> inspectActiveGraph();
}
