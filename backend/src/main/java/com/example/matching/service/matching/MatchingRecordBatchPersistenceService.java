package com.example.matching.service.matching;

import com.example.matching.entity.matching.MatchingRecord;

import java.util.Collection;

/**
 * Independent batch persistence service for matching records.
 * <p>
 * Uses MyBatis-Plus batch executor (not per-item insert).
 * This service has no reverse dependency on MatchingExecuteService or MatchingRecordService
 * to avoid circular dependency.
 */
public interface MatchingRecordBatchPersistenceService {

    /**
     * Persist a batch of matching records using the batch executor.
     *
     * @param records the records to persist
     * @return true if all records were saved successfully
     */
    boolean saveBatch(Collection<MatchingRecord> records);
}
