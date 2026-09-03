package com.example.matching.service.rag.impl;

import com.example.matching.entity.rag.RagQueryLog;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class RagQueryLogServiceImplTest {

    @Test
    void savesQueryLogsInAnIndependentWriteTransaction() throws Exception {
        Transactional transactional = RagQueryLogServiceImpl.class
                .getMethod("saveQueryLog", RagQueryLog.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
