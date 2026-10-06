package com.example.matching.service.ability;

import com.example.matching.entity.ability.AgentMemoryHitLog;
import com.example.matching.mapper.ability.AgentMemoryHitLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryHitLogArchiveService {

    private final AgentMemoryHitLogMapper hitLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void archiveBatch(List<Long> ids) {
        int batchRows = ids.size();

        hitLogMapper.archiveByIds(ids);
        long archivedRows = hitLogMapper.countArchivedByIds(ids);
        if (archivedRows != batchRows) {
            throw new IllegalStateException("Archive verification failed: expected=" + batchRows
                    + ", actual=" + archivedRows);
        }
        int deletedRows = hitLogMapper.physicalDeleteByIds(ids);
        if (deletedRows != batchRows) {
            throw new IllegalStateException("Source delete verification failed: expected=" + batchRows
                    + ", actual=" + deletedRows);
        }
    }
}
