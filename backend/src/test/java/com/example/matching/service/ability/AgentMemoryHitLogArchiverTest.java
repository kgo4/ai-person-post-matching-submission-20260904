package com.example.matching.service.ability;

import com.example.matching.entity.ability.AgentMemoryHitLog;
import com.example.matching.mapper.ability.AgentMemoryHitLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMemoryHitLogArchiverTest {

    @Test
    void archivesAndVerifiesRowsBeforePhysicallyDeletingSourceRows() {
        AgentMemoryHitLogMapper mapper = mock(AgentMemoryHitLogMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AgentMemoryHitLogArchiveService archiveService = mock(AgentMemoryHitLogArchiveService.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(mapper.selectCount(any())).thenReturn(10_000L);
        when(mapper.selectList(any())).thenReturn(List.of(expiredLog(8L)), List.of());

        AgentMemoryHitLogArchiver archiver = new AgentMemoryHitLogArchiver(
                mapper, redis, archiveService);

        archiver.doArchive();

        verify(archiveService).archiveBatch(List.of(8L));
    }

    @Test
    void preservesSourceRowsWhenArchiveVerificationFails() {
        AgentMemoryHitLogMapper mapper = mock(AgentMemoryHitLogMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AgentMemoryHitLogArchiveService archiveService = mock(AgentMemoryHitLogArchiveService.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(mapper.selectCount(any())).thenReturn(10_000L);
        when(mapper.selectList(any())).thenReturn(List.of(expiredLog(9L)));
        doThrow(new IllegalStateException("Archive verification failed: expected=1, actual=0"))
                .when(archiveService).archiveBatch(List.of(9L));

        AgentMemoryHitLogArchiver archiver = new AgentMemoryHitLogArchiver(
                mapper, redis, archiveService);

        org.assertj.core.api.Assertions.assertThatThrownBy(archiver::doArchive)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Archive verification failed");

        verify(mapper, never()).physicalDeleteByIds(anyList());
    }

    private AgentMemoryHitLog expiredLog(Long id) {
        AgentMemoryHitLog log = new AgentMemoryHitLog();
        log.setId(id);
        log.setHitTime(LocalDateTime.now().minusDays(100));
        return log;
    }
}
