package com.example.matching.service.matching;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MatchingRecordPersistenceService extends ServiceImpl<MatchingRecordMapper, MatchingRecord> {

    @Transactional
    public void saveAll(List<MatchingRecord> records) {
        saveBatch(records, 500);
    }

    @Transactional
    public void updateAll(List<MatchingRecord> records) {
        updateBatchById(records, 500);
    }
}
