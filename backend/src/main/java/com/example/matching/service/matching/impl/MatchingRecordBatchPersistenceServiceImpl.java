package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.MatchingRecordBatchPersistenceService;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Batch persistence service for matching records.
 * <p>
 * Inherits MyBatis-Plus {@code saveBatch} which uses the batch executor.
 * No custom SQL or per-item insert logic.
 */
@Service
public class MatchingRecordBatchPersistenceServiceImpl
        extends ServiceImpl<MatchingRecordMapper, MatchingRecord>
        implements MatchingRecordBatchPersistenceService {

    @Override
    public boolean saveBatch(Collection<MatchingRecord> records) {
        if (records == null || records.isEmpty()) {
            return true;
        }
        return super.saveBatch(records, records.size());
    }
}
