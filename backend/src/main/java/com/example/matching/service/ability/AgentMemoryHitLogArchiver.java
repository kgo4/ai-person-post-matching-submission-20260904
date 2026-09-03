package com.example.matching.service.ability;

import com.example.matching.entity.ability.AgentMemoryHitLog;
import com.example.matching.mapper.ability.AgentMemoryHitLogMapper;
import com.example.matching.schedule.ScheduledTaskRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class AgentMemoryHitLogArchiver {

    private static final String REDIS_CURSOR_KEY = "matching:memory:hit-log:archive:cursor";
    private static final int BATCH_SIZE = 500;
    private static final long MIN_ROWS_FOR_ARCHIVE = 10000L;
    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final AgentMemoryHitLogMapper hitLogMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AgentMemoryHitLogArchiveService archiveService;

    @Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Value("${agent.memory.hit-log.archive.enabled:true}")
    private boolean archiveEnabled;

    @Value("${agent.memory.hit-log.archive.retention-days:90}")
    private int retentionDays;

    public AgentMemoryHitLogArchiver(AgentMemoryHitLogMapper hitLogMapper,
                                      StringRedisTemplate stringRedisTemplate,
                                      AgentMemoryHitLogArchiveService archiveService) {
        this.hitLogMapper = hitLogMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.archiveService = archiveService;
    }

    @Scheduled(cron = "${agent.memory.hit-log.archive.cron:0 0 3 * * ?}")
    public void archiveExpiredHitLogs() {
        if (taskRunner != null) {
            taskRunner.run("agent_memory_hit_log_archiver", this::archiveInternal);
        } else {
            archiveInternal();
        }
    }

    private void archiveInternal() {
        if (!archiveEnabled) {
            log.debug("Agent memory hit log archiver is disabled");
            return;
        }

        try {
            doArchive();
        } catch (Exception e) {
            log.error("Agent memory hit log archiver failed", e);
        }
    }

    @Transactional
    public void doArchive() {
        long totalRows = hitLogMapper.selectCount(null);
        if (totalRows < MIN_ROWS_FOR_ARCHIVE) {
            log.debug("Agent memory hit log total rows {} < {}, skipping archive", totalRows, MIN_ROWS_FOR_ARCHIVE);
            return;
        }

        long lastProcessedId = readCursor();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS);

        int totalArchived = 0;
        int totalChecksum = 0;
        long maxId = lastProcessedId;

        while (true) {
            List<AgentMemoryHitLog> batch = hitLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentMemoryHitLog>()
                            .gt(lastProcessedId > 0, AgentMemoryHitLog::getId, lastProcessedId)
                            .lt(AgentMemoryHitLog::getHitTime, cutoff)
                            .orderByAsc(AgentMemoryHitLog::getId)
                            .last("LIMIT " + BATCH_SIZE));

            if (batch.isEmpty()) {
                break;
            }

            int batchRows = batch.size();

            for (AgentMemoryHitLog log : batch) {
                if (log.getId() > maxId) {
                    maxId = log.getId();
                }
            }

            List<Long> ids = batch.stream().map(AgentMemoryHitLog::getId).toList();
            archiveService.archiveBatch(ids);

            totalArchived += batchRows;
            totalChecksum += batchRows;

            lastProcessedId = maxId;
            saveCursor(maxId);

            if (batchRows < BATCH_SIZE) {
                break;
            }
        }

        if (totalArchived > 0) {
            log.info("Agent memory hit log archived: rows={}, checksum={}, cursor={}", totalArchived, totalChecksum, maxId);
        }
    }

    private long readCursor() {
        try {
            String value = stringRedisTemplate.opsForValue().get(REDIS_CURSOR_KEY);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("Failed to read hit log archive cursor, using 0: {}", e.getMessage());
            return 0L;
        }
    }

    private void saveCursor(long cursor) {
        try {
            stringRedisTemplate.opsForValue().set(REDIS_CURSOR_KEY, String.valueOf(cursor));
        } catch (Exception e) {
            log.warn("Failed to save hit log archive cursor: {}", e.getMessage());
        }
    }
}
