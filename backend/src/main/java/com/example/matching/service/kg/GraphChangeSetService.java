package com.example.matching.service.kg;

import com.example.matching.entity.kg.KgGraphChangeSet;

import java.util.Map;
import java.util.List;

public interface GraphChangeSetService {

    KgGraphChangeSet requestChange(String sourceType, String entityType, Long entityId,
                                   String operationType, Map<String, Object> payload, Long createdBy);

    void executeChange(String changeCode);

    void republishPendingChanges();

    KgGraphChangeSet getChange(String changeCode);

    List<KgGraphChangeSet> listChanges(String processStatus, Integer limit);

    void recoverZombieChangeSets();
}
