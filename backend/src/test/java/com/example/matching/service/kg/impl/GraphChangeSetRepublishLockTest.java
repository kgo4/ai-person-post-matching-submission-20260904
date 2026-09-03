package com.example.matching.service.kg.impl;

import com.example.matching.mapper.kg.KgGraphChangeSetMapper;
import com.example.matching.mapper.kg.KgGraphBuildTaskMapper;
import com.example.matching.schedule.SchedulerMetrics;
import com.example.matching.service.common.DistributedLockService;
import com.example.matching.service.kg.KnowledgeGraphIncrementalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphChangeSetRepublishLockTest {

    private KgGraphChangeSetMapper changeSetMapper;
    private DistributedLockService distributedLockService;
    private ApplicationEventPublisher eventPublisher;
    private GraphChangeSetServiceImpl service;

    @BeforeEach
    void setUp() {
        changeSetMapper = mock(KgGraphChangeSetMapper.class);
        distributedLockService = mock(DistributedLockService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new GraphChangeSetServiceImpl(
                changeSetMapper,
                mock(KgGraphBuildTaskMapper.class),
                mock(KnowledgeGraphIncrementalService.class),
                eventPublisher,
                new ObjectMapper(),
                distributedLockService,
                mock(SchedulerMetrics.class));
    }

    @Test
    void lockNotAcquiredSkipsRepublishQuery() {
        when(distributedLockService.tryAcquire("kg-graph-change-republish"))
                .thenReturn(null);

        service.republishPendingChanges();

        verify(changeSetMapper, never()).selectList(any());
    }

    @Test
    void lockAcquiredQueriesAndReleasesLock() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("kg-graph-change-republish"))
                .thenReturn(handle);
        when(changeSetMapper.selectList(any())).thenReturn(List.of());

        service.republishPendingChanges();

        verify(changeSetMapper).selectList(any());
        verify(handle).close();
    }
}
